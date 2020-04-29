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

package eportfolium.com.karuta.webapp.rest.controller;

import eportfolium.com.karuta.business.contract.PortfolioManager;
import eportfolium.com.karuta.webapp.annotation.InjectLogger;
import eportfolium.com.karuta.webapp.rest.provider.mapper.exception.RestWebApplicationException;
import eportfolium.com.karuta.webapp.util.UserInfo;
import eportfolium.com.karuta.webapp.util.javaUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * Managing and listing portfolio groups.
 *
 * @author mlengagne
 *
 */
@RestController
@RequestMapping("/portfoliogroups")
public class PortfolioGroupController extends AbstractController {

    @InjectLogger
    private static Logger logger;

    @Autowired
    private PortfolioManager portfolioManager;

    /**
     * Create a new portfolio group. <br>
     * POST /rest/api/portfoliogroups
     *
     * @param groupname          Name of the group we are creating
     * @param type               group/portfolio
     * @param parent             parentid
     * @param request
     * @return groupid
     */

    @PostMapping
    public ResponseEntity<String> postPortfolioGroup(@RequestParam("label") String groupname,
                                       @RequestParam("type") String type,
                                       @RequestParam("parent") Long parent,
                                       HttpServletRequest request) throws RestWebApplicationException {
        UserInfo ui = checkCredential(request, null, null, null); // FIXME
        Long response = -1L;

        // Check type value
        try {
            response = portfolioManager.addPortfolioGroup(groupname, type, parent, ui.userId);
            logger.debug("Portfolio group " + groupname + " created");

            if (response == -1) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).body("Error in creation");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex.getMessage() + "\n\n" + javaUtils.getCompleteStackTrace(ex));
            throw new RestWebApplicationException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        }

        return ResponseEntity.ok(Long.toString(response));
    }

    /**
     * Put a portfolio in portfolio group. <br>
     * PUT /rest/api/portfoliogroups
     *
     * @param group              group id
     * @param uuid               portfolio id
     * @param label
     * @param request
     * @return Code 200
     */
    @PutMapping
    public ResponseEntity<String> putPortfolioInPortfolioGroup(@RequestParam("group") Long group,
                                                 @RequestParam("uuid") String uuid,
                                                 @RequestParam("label") String label,
                                                 HttpServletRequest request) throws RestWebApplicationException {
        UserInfo ui = checkCredential(request, null, null, null);

        try {
            int response = -1;
            response = portfolioManager.addPortfolioInGroup(uuid, group, label, ui.userId); // FIXME
            logger.debug("Portfolio added  in group " + label);
            return ResponseEntity.ok(Integer.toString(response));
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex.getMessage() + "\n\n" + javaUtils.getCompleteStackTrace(ex));
            throw new RestWebApplicationException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    /**
     * Get portfolio by portfoliogroup, or if there's no group id give, give the
     * list of portfolio group GET /rest/api/portfoliogroups<br>
     *
     * - Without group id <groups> <group id={groupid}> <label>{group name}</label>
     * </group> ... </groups>
     *
     * - With group id <group id={groupid}> <portfolio id={uuid}></portfolio> ...
     * </group>
     *
     * @param group              group id
     * @param portfolioUuid
     * @param groupLabel         group label
     * @param request
     * @return group id or empty str if group id not found
     */
    @GetMapping
    public String getPortfolioByPortfolioGroup(@RequestParam("group") Long group,
                                               @RequestParam("uuid") String portfolioUuid,
                                               @RequestParam("label") String groupLabel,
                                               HttpServletRequest request) throws RestWebApplicationException {
        UserInfo ui = checkCredential(request, null, null, null);
        String xmlUsers = "";

        try {
            if (groupLabel != null) {
                Long groupid = portfolioManager.getPortfolioGroupIdFromLabel(groupLabel, ui.userId);
                if (groupid == -1) {
                    throw new RestWebApplicationException(HttpStatus.NOT_FOUND, "");
                }
                xmlUsers = Long.toString(groupid);
            } else if (portfolioUuid != null) {
                xmlUsers = portfolioManager.getPortfolioGroupListFromPortfolio(portfolioUuid);
            } else if (group == null)
                xmlUsers = portfolioManager.getPortfolioGroupList();
            else
                xmlUsers = portfolioManager.getPortfoliosByPortfolioGroup(group);
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex.getMessage() + "\n\n" + javaUtils.getCompleteStackTrace(ex));
            throw new RestWebApplicationException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        }

        return xmlUsers;
    }

    /**
     * Remove a portfolio from a portfolio group, or remove a portfoliogroup. <br>
     * DELETE /rest/api/portfoliogroups
     *
     * @param groupId            group id
     * @param uuid               portfolio id
     * @param request
     * @return Code 200
     */
    @DeleteMapping
    public String deletePortfolioByPortfolioGroup(@RequestParam("group") long groupId,
                                                  @RequestParam("uuid") String uuid,
                                                  HttpServletRequest request) throws RestWebApplicationException {
//		checkCredential(httpServletRequest, null, null, null); //FIXME
        boolean response = false;
        try {
            if (uuid == null)
                response = portfolioManager.removePortfolioGroups(groupId);
            else
                response = portfolioManager.removePortfolioFromPortfolioGroups(uuid, groupId);

        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex.getMessage() + "\n\n" + javaUtils.getCompleteStackTrace(ex));
            throw new RestWebApplicationException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
        return String.valueOf(response);
    }

}

