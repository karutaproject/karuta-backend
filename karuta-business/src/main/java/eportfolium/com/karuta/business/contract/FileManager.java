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

package eportfolium.com.karuta.business.contract;

import eportfolium.com.karuta.document.ResourceDocument;
import org.apache.http.client.HttpClient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface FileManager {

	boolean rewriteFile(String sessionid, String backend, String user, UUID id, String lang, File file)
			throws Exception;

	String[] findFiles(String directoryPath, String id);

	String unzip(String zipFile, String destinationFolder) throws IOException;

	boolean updateResource(ResourceDocument resource,
						   InputStream content,
						   String lang,
						   boolean thumbnail);

	HttpClient createClient();
}
