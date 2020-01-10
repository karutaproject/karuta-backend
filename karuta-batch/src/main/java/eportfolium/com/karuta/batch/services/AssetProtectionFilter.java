//
// This is necessary due to https://issues.apache.org/jira/browse/TAP5-815 .
// Based on an entry in the Users mailing list by martijn.list on 15 Aug 09.
//

package eportfolium.com.karuta.batch.services;

import java.io.IOException;
import java.util.Set;

import org.apache.tapestry5.Link;
import org.apache.tapestry5.services.PageRenderLinkSource;
import org.apache.tapestry5.services.Request;
import org.apache.tapestry5.services.RequestFilter;
import org.apache.tapestry5.services.RequestHandler;
import org.apache.tapestry5.services.Response;

import eportfolium.com.karuta.batch.pages.infra.AccessDenied;
import eportfolium.com.karuta.batch.util.ResourceUtil;

public class AssetProtectionFilter implements RequestFilter {

	private final Set<String> assetsWhiteList;
	private final PageRenderLinkSource pageRenderLinkSource;

	public AssetProtectionFilter(Set<String> assetsWhiteList, PageRenderLinkSource pageRenderLinkSource) {
		super();
		this.assetsWhiteList = assetsWhiteList;
		this.pageRenderLinkSource = pageRenderLinkSource;
	}

	@Override
	public boolean service(Request request, Response response, RequestHandler handler) throws IOException {
		String path = request.getPath();

		// Block access to WEB-INF/ and META-INF/.

		if (path.contains("/WEB-INF/") || path.contains("/META-INF/")) {
			Link accessDenied = pageRenderLinkSource.createPageRenderLink(AccessDenied.class);
			response.sendRedirect(accessDenied);
			return true; // Make sure we leave the chain
		}

		// Block access to assets whose file extensions aren't in our white list.
		// We need this because by default Tapestry serves up the entire JAR/WAR file
		// under /assets/.

		if (path.startsWith("/assets") && !assetsWhiteList.contains(ResourceUtil.getExtension(path).toLowerCase())) {
			Link accessDenied = pageRenderLinkSource.createPageRenderLink(AccessDenied.class);
			response.sendRedirect(accessDenied);
			return true; // Make sure we leave the chain
		}

		return handler.service(request, response);
	}

}
