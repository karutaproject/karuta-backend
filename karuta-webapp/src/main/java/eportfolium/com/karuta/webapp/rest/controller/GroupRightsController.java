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

import eportfolium.com.karuta.business.contract.GroupManager;
import eportfolium.com.karuta.model.exception.BusinessException;
import eportfolium.com.karuta.webapp.annotation.InjectLogger;
import eportfolium.com.karuta.webapp.util.UserInfo;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/groupRights")
public class GroupRightsController extends AbstractController {

    @Autowired
    private GroupManager groupManager;

    @InjectLogger
    private static Logger logger;

    /**
     * Get rights in a role from a groupid <br>
     * GET /rest/api/groupRights
     *
     * @param groupId            role id
     * @param request
     * @return <groupRights> <groupRight gid="groupid" templateId="grouprightid>
     *         <item AD="True/False" creator="uid"; date=""; DL="True/False" id=uuid
     *         owner=uid"; RD="True/False" SB="True"/"False" typeId=" ";
     *         WR="True/False"/>"; </groupRight> </groupRights>
     */
    @GetMapping(produces = "application/xml")
    public String getGroupRights(@RequestParam("group") long groupId,
                                 HttpServletRequest request) throws Exception {

        UserInfo ui = checkCredential(request);

        return groupManager.getGroupRights(ui.userId, groupId);
    }

    /**
     * Delete a right definition. <br>
     * DELETE /rest/api/groupRights
     *
     * @param groupId
     * @param request
     * @return
     */
    @DeleteMapping(produces = "application/xml")
    public String deleteGroupRights(@RequestParam("group") long groupId,
                                    HttpServletRequest request) throws BusinessException {
        UserInfo ui = checkCredential(request);

        groupManager.removeRights(groupId, ui.userId);

        return "supprimé";
    }
}

