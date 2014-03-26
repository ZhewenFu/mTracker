package com.ebay.build.dal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import oracle.jdbc.OraclePreparedStatement;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.ebay.build.profiler.model.Session;

public class SessionJDBCTemplate {
	private DataSource dataSource;
	private JdbcTemplate jdbcTemplateObject;

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.jdbcTemplateObject = new JdbcTemplate(dataSource);
	}

	public int create(final Session session) {
		final String SQL = "insert into RBT_SESSION (pool_name, machine_name, user_name, environment, " +
				"status, duration, maven_version, goals, start_time, git_url, git_branch, jekins_url, java_version, cause, full_stacktrace, raptor_version, domain_version, category, filter) " +
				"values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplateObject.update(new PreparedStatementCreator() {
			public PreparedStatement createPreparedStatement(
					Connection connection) throws SQLException {
				PreparedStatement ps = connection.prepareStatement(SQL,
						new String[] { "id" });
				ps.setString(1, session.getAppName());
				ps.setString(2, session.getMachineName());
				ps.setString(3, session.getUserName());
				ps.setString(4, session.getEnvironment());
				ps.setString(5, session.getStatus());
				ps.setLong(6, session.getDuration());
				ps.setString(7, session.getMavenVersion());
				ps.setString(8, session.getGoals());
				ps.setTimestamp(9, new java.sql.Timestamp(session.getStartTime().getTime()));
				ps.setString(10,  session.getGitUrl());
				ps.setString(11, session.getGitBranch());
				ps.setString(12, session.getJekinsUrl());
				ps.setString(13, session.getJavaVersion());
				ps.setString(14, getCause(session.getExceptionMessage()));
				
				setFullStackTraceAsClob(15, ps, session);
				
				ps.setString(16, session.getRaptorVersion());
				ps.setString(17, session.getDomainVersion());
				
				ps.setString(18,  session.getCategory());
				ps.setString(19,  session.getFilter());

				return ps;
			}
		}, keyHolder);
		
		return keyHolder.getKey().intValue();
	}
	
	private void setFullStackTraceAsClob(int index, PreparedStatement ps, Session session) {
		String content = "";
		if (session.getFullStackTrace() != null) {
			content = session.getFullStackTrace();
		}
		
		try {
			((OraclePreparedStatement) ps).setStringForClob(index, content);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private String getCause(String message) {
		if (message != null && message.length() > 800) {
			return message.substring(0, 750);
		}
		return message;
	}

	public Session getSession(Integer id) {
		String SQL = "select * from Session where id = ?";
		Session student = jdbcTemplateObject.queryForObject(SQL,
				new Object[] { id }, new SessionMapper());
		return student;
	}

	public List<Session> listSessions() {
		String SQL = "select * from Session";
		List<Session> students = jdbcTemplateObject.query(SQL,
				new SessionMapper());
		return students;
	}

	public void delete(Integer id) {
		String SQL = "delete from Session where id = ?";
		jdbcTemplateObject.update(SQL, id);
		System.out.println("Deleted Record with ID = " + id);
		return;
	}
	
	public List<Session> getExpSessionWithNullCategory() {
		String SQL = "select * from RBT_SESSION " +
				"where cause is not null " +
				" and (category is null or filter is null) " +
				" and start_time > sysdate - 10";
		
		return jdbcTemplateObject.query(SQL, new SessionMapper());
	}
	
	public void updateCategory(Session session) {
		String SQL = "update RBT_SESSION set category = ?,  filter = ? where id = ?";
		jdbcTemplateObject.update(SQL, new Object[] {session.getCategory(), session.getFilter(), session.getId()});
	}
	
	public int[] batchUpdateAssemblyBreakdown(final List<AssemblyBreakdown> durations) {
		int[] updateCounts = jdbcTemplateObject.batchUpdate(
				"update RBT_SESSION set duration_assembly_package= duration_build - ?, duration_assembly_upload = ?, duration_assembly_service = ? where jekins_url = ? and goals like 'com.ebay.raptor.build:assembler-maven-plugin:%:deploy' and status = 0",
				new BatchPreparedStatementSetter() {
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						ps.setInt(1, durations.get(i).getUploadDuration() + durations.get(i).getServiceDuration());
						ps.setInt(2, durations.get(i).getUploadDuration());
						ps.setInt(3, durations.get(i).getServiceDuration());
						ps.setString(4, durations.get(i).getJenkinsUrl());
					}

					public int getBatchSize() {
						return durations.size();
					}
				});
		return updateCounts;
	}
}
