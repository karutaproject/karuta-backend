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

package eportfolium.com.karuta.consumer.contract.dao;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import eportfolium.com.karuta.model.bean.GroupInfo;
import eportfolium.com.karuta.model.bean.GroupRightInfo;
import eportfolium.com.karuta.model.exception.DoesNotExistException;

public interface GroupInfoDao {

	void persist(GroupInfo transientInstance);

	void remove(GroupInfo persistentInstance);

	GroupInfo merge(GroupInfo detachedInstance);

	GroupInfo findById(Serializable id) throws DoesNotExistException;

	void removeById(final Serializable id) throws DoesNotExistException;

	Long add(GroupRightInfo grid, long owner, String label);

	List<GroupInfo> getGroupsByRole(String portfolioUuid, String role);

	/**
	 * Récupère une instance de group_info associé à un grid.
	 * 
	 * @param grid
	 * @return
	 */
	GroupInfo getGroupByGrid(Long grid);

	List<GroupInfo> getByPortfolio(String portfolioUuid);

	boolean exists(String label);

	boolean exists(String label, Long owner);

	ResultSet findAll(String table, Connection con);

	ResultSet getMysqlGroupRightsInfos(Connection con) throws SQLException;

	void removeAll();

	List<GroupInfo> getGroups(String label, Long owner);

	List<GroupInfo> getGroups(Long owner);

}