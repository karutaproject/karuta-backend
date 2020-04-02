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