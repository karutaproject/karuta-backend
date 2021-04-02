package eportfolium.com.karuta.document.conversion;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import eportfolium.com.karuta.document.NodeDocument;
import eportfolium.com.karuta.document.NodeList;
import eportfolium.com.karuta.document.ResourceDocument;

import java.io.IOException;

public class NodeListDocumentSerializer extends JsonSerializer<NodeList> {
		@Override
		public void serialize( NodeList value, JsonGenerator gen,
				SerializerProvider serializers ) throws IOException
		{
			ObjectMapper mapper = new XmlMapper();
			gen.writeStartObject();
			
			for( NodeDocument n : value.getNodes() )
			{
				gen.writeRaw(String.format("<node id=\"%s\">", n.getId()));
				
				String metadata = mapper.writeValueAsString(n.getMetadata());
				String metadata_epm = mapper.writeValueAsString(n.getMetadataEpm());
				String metadata_wad = mapper.writeValueAsString(n.getMetadataWad());
				StringBuilder sb = new StringBuilder();
				sb.append(metadata).append(metadata_epm).append(metadata_wad);
				for( ResourceDocument r : n.getResources() )
				{
					String convertedContent = mapper.writeValueAsString(r);
					/// 2 times because Spring convert tags when sending data
					convertedContent = convertedContent.replace("&", "&amp;");
					convertedContent = convertedContent.replace("&", "&amp;");

					sb.append(convertedContent);
				}
				/// Should just have a toString with hibernate DB objects
				gen.writeRaw(String.format("<%s id=\"%s\">%s", n.getType(), n.getId(), sb.toString()));
				gen.writeRaw(String.format("</%s>", n.getType()));
				
				
				gen.writeRaw("</node>");
			}
			gen.writeEndObject();
		}
}
