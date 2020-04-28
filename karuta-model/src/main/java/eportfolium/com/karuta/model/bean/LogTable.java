/* =======================================================
	Copyright 2020 - ePortfolium - Licensed under the
	Educational Community License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may
	obtain a copy of the License at

	http://www.osedu.org/licenses/ECL-2.0

	Unless required by applicable law or agreed to in writing,
	software distributed under the License is distributed on an "AS IS"
	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
	or implied. See the License for the specific language governing
	permissions and limitations under the License.
   ======================================================= */

package eportfolium.com.karuta.model.bean;
// Generated 13 juin 2019 19:14:13 by Hibernate Tools 5.2.10.Final

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 * LogTable generated by hbm2java
 */
@Entity
@Table(name = "log_table")
public class LogTable implements Serializable {

	private static final long serialVersionUID = -6261073688871677480L;

	private Integer id;
	private Date logDate;
	private String logUrl;
	private String logMethod;
	private String logHeaders;
	private String logInBody;
	private String logOutBody;
	private int logCode;

	public LogTable() {
	}

	public LogTable(Integer id, Date logDate, String logUrl, String logMethod, String logHeaders, int logCode) {
		this.id = id;
		this.logDate = logDate;
		this.logUrl = logUrl;
		this.logMethod = logMethod;
		this.logHeaders = logHeaders;
		this.logCode = logCode;
	}

	public LogTable(Integer id, Date logDate, String logUrl, String logMethod, String logHeaders, String logInBody,
			String logOutBody, int logCode) {
		this.id = id;
		this.logDate = logDate;
		this.logUrl = logUrl;
		this.logMethod = logMethod;
		this.logHeaders = logHeaders;
		this.logInBody = logInBody;
		this.logOutBody = logOutBody;
		this.logCode = logCode;
	}

	@Id
	@Column(name = "log_id", unique = true, nullable = false)
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "log_date", nullable = false, length = 19)
	public Date getLogDate() {
		return this.logDate;
	}

	public void setLogDate(Date logDate) {
		this.logDate = logDate;
	}

	@Column(name = "log_url", nullable = false)
	public String getLogUrl() {
		return this.logUrl;
	}

	public void setLogUrl(String logUrl) {
		this.logUrl = logUrl;
	}

	@Column(name = "log_method", nullable = false, length = 50)
	public String getLogMethod() {
		return this.logMethod;
	}

	public void setLogMethod(String logMethod) {
		this.logMethod = logMethod;
	}

	@Lob
	@Column(name = "log_headers", nullable = false)
	public String getLogHeaders() {
		return this.logHeaders;
	}

	public void setLogHeaders(String logHeaders) {
		this.logHeaders = logHeaders;
	}

	@Lob
	@Column(name = "log_in_body")
	public String getLogInBody() {
		return this.logInBody;
	}

	public void setLogInBody(String logInBody) {
		this.logInBody = logInBody;
	}

	@Lob
	@Column(name = "log_out_body")
	public String getLogOutBody() {
		return this.logOutBody;
	}

	public void setLogOutBody(String logOutBody) {
		this.logOutBody = logOutBody;
	}

	@Column(name = "log_code", nullable = false)
	public int getLogCode() {
		return this.logCode;
	}

	public void setLogCode(int logCode) {
		this.logCode = logCode;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LogTable other = (LogTable) obj;
		if (getId() == null) {
			if (other.getId() != null)
				return false;
		} else if (!getId().equals(other.getId()))
			return false;
		return true;
	}

}
