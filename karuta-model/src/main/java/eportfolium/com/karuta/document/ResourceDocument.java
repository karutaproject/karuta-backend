package eportfolium.com.karuta.document;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import eportfolium.com.karuta.model.bean.Node;
import eportfolium.com.karuta.model.bean.Resource;

import java.io.IOException;
import java.util.*;

@JsonRootName("asmResource")
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class ResourceDocument {
    private UUID id;
    private UUID nodeId;
    private String xsiType;
    private Date modifDate;
    private String content = "";

    private String code;

    public abstract static class TagWithLang {
        protected String lang;
        protected String value;

        public TagWithLang() { }

        public TagWithLang(String lang, String value) {
            this.lang = lang;
            this.value = value;
        }

        @JsonProperty("lang")
        public String getLang() {
            return lang;
        }

        @JacksonXmlText
        public String getValue() {
            return value;
        }
    }

    static class FilenameTag extends TagWithLang {
        public FilenameTag() { }

        public FilenameTag(String lang, String value) {
            super(lang, value);
        }
    }

    static class FileidTag extends TagWithLang {
        public FileidTag() { }

        public FileidTag(String lang, String value) {
            super(lang, value);
        }
    }

    static class TypeTag extends TagWithLang {
        public TypeTag() { }

        public TypeTag(String lang, String value) {
            super(lang, value);
        }
    }


    // For file resources
    private List<FilenameTag> filename;
    private List<FileidTag> fileid = new ArrayList<FileidTag>();
    private List<TypeTag> type;

    public ResourceDocument() { }

    public ResourceDocument(UUID id) {
        this.id = id;
    }

    public ResourceDocument(Resource resource, Node node) {
        this(resource.getId());

        this.nodeId = node.getId();
        this.xsiType = resource.getXsiType();
        this.modifDate = resource.getModifDate();

        this.content = resource.getContent();
        if( this.content == null ) this.content = "";

        if (resource.getContent() != null) {
            try {
                XmlMapper mapper = new XmlMapper();
                ResourceDocument subset = mapper.readerFor(ResourceDocument.class)
                        .readValue("<asmResource>" + resource.getContent() + "</asmResource>");

                this.filename = subset.getFilename();
                this.fileid = subset.getFileid();
                if( this.fileid == null ) this.fileid = new ArrayList<FileidTag>(); 
                this.type = subset.getType();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @JsonGetter("id")
    @JacksonXmlProperty(isAttribute = true)
    public UUID getId() {
        return id;
    }

    @JsonGetter("contextid")
    @JacksonXmlProperty(isAttribute = true, localName = "contextid")
    public UUID getNodeId() {
        return nodeId;
    }

    public void setNodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    @JsonGetter("xsi_type")
    @JacksonXmlProperty(isAttribute = true, localName = "xsi_type")
    public String getXsiType() {
        return xsiType;
    }

    @JsonGetter("last_modif")
    @JacksonXmlProperty(isAttribute = true, localName = "last_modif")
    @JsonDeserialize(using = DateDeserializer.class)
    public Date getModifDate() {
        return modifDate;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @JsonGetter("code")
    @JsonRawValue
    public String getCode() {
        return code;
    }

    @JsonRawValue
    public void setCode(String code) {
        this.code = code;

        if (code != null) {
	        this.content += "<code>" + code + "</code>";
        }
    }

    @JsonGetter("filename")
    @JacksonXmlElementWrapper(useWrapping = false)
    public List<FilenameTag> getFilename() {
        return filename;
    }

    public String getFilename(String lang) {
        return findValueForLang(filename, lang);
    }

    public void setFilename(List<FilenameTag> filename) {
        this.filename = filename;
        this.dumpMap("filename", filename);
    }


    @JsonGetter("fileid")
    @JacksonXmlElementWrapper(useWrapping = false)
    public List<FileidTag> getFileid() {
        return fileid;
    }

    public String getFileid(String lang) {
        return findValueForLang(fileid, lang);
    }

    public void setFileid(List<FileidTag> fileid) {
        this.fileid = fileid;
        this.dumpMap("fileid", fileid);
    }

    public void setFileid(String lang, String fileid) {
			/// Clear previous fileid tag before reconstruction
			String fileidpat = "<fileid.*?/fileid>";
			this.content = this.content.replaceAll(fileidpat, "");
			
			/// Replace previous value
    	boolean exist = false;
    	for( FileidTag l : this.fileid )	// Not great, eh.
    	{
    		if( l.lang.equals(lang) )
    		{
    			exist = true;
    			l.value = fileid;
					
    			break;
    		}
    	}
    	if( !exist )
  		{
    		FileidTag tag = new FileidTag(lang, fileid);
    		this.fileid.add(tag);
  		}
    	
      this.dumpMap("fileid", this.fileid);
    }

    @JsonGetter("type")
    @JacksonXmlElementWrapper(useWrapping = false)
    public List<TypeTag> getType() { return type; }

    public String getType(String lang) {
        return findValueForLang(type, lang);
    }

    public void setType(List<TypeTag> type) {
        this.type = type;
        this.dumpMap("type", type);
    }

    @JsonRawValue
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public String getContent() {
        return content;
    }

    /// Raw value does nothing, it's already processed
    @JsonRawValue
    @JsonAnySetter
    public void ignored(String name, Object value) {
        StringBuilder builder = new StringBuilder("<");
        builder.append(name);

        if (value instanceof Map) {
            Map<String, Object> attributes = (Map<String, Object>)value;

            attributes.forEach((n, v) -> {
                if (!n.equals(""))
                    builder.append(" ").append(n).append("=\"").append(v).append("\"");
            });

            builder.append(">");
            if( attributes.get("") != null )
            {
            	String processed = attributes.get("").toString();
            	processed = processed.replace("&", "&amp;");
            	processed = processed.replace("<", "&lt;");
            	processed = processed.replace(">", "&gt;");
            	
                builder.append(processed);
            }
        } else {
        	builder.append(">");
        	if( value != null )
            builder.append(value);
        }

        builder.append("</")
                .append(name)
                .append(">");

        content += builder.toString();
    }

    private String findValueForLang(List<? extends TagWithLang> values, String lang) {
        if (values == null)
            return "";

        Optional<? extends TagWithLang> search = values.stream()
            .filter(f -> f.getLang().equals(lang))
            .findFirst();

        return search
                .map(TagWithLang::getValue)
                .orElse("");
    }

    private void dumpMap(String name, List<? extends TagWithLang> values) {
        if (this.content == null)
            this.content = "";

        StringBuilder stringBuilder = new StringBuilder();

        values.forEach(tag -> {
            stringBuilder.append("<").append(name)
                    .append(" lang=\"").append(tag.getLang()).append("\">")
                    .append(tag.getValue())
                    .append("</").append(name).append(">");

        });

        content += stringBuilder.toString();
    }
}
