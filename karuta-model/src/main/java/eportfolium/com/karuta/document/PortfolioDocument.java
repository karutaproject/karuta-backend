package eportfolium.com.karuta.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import eportfolium.com.karuta.model.bean.Node;
import eportfolium.com.karuta.model.bean.Portfolio;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonRootName("portfolio")
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class PortfolioDocument {
    private UUID id;
    private UUID rootNodeId;

    private String code;
    private int version;
    private Long gid;

    @JsonDeserialize(using = BooleanDeserializer.class)
    private boolean owner;

    private Long ownerId;

    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss.S")
    private Date modifDate;

    private List<NodeDocument> nodes = new ArrayList<>();

    public PortfolioDocument() { }

    public PortfolioDocument(UUID id) {
        this.version = 4;
        this.id = id;
    }

    public PortfolioDocument(UUID id, Long gid) {
        this(id);

        this.gid = gid;
    }

    public PortfolioDocument(UUID id, boolean owner) {
        this(id);

        this.owner = owner;
        this.code = "";
    }

    public PortfolioDocument(UUID id, boolean owner, String code, List<NodeDocument> nodes) {
        this(id, owner);

        this.code = code;
        this.nodes = nodes;
    }

    public PortfolioDocument(Portfolio portfolio, boolean owner) {
        this(portfolio.getId(), owner);

        Node rootNode = portfolio.getRootNode();

        this.rootNodeId = rootNode.getId();
        this.ownerId = portfolio.getModifUserId();
        this.modifDate = portfolio.getModifDate();

        NodeDocument child = new NodeDocument(rootNode);

        child.setLabel(rootNode.getLabel());
        child.setDescription(rootNode.getDescr());
        child.setSemtag(rootNode.getSemtag());

        try {
            child.setMetadataWad(MetadataWadDocument.from(rootNode.getMetadataWad()));
            child.setMetadataEpm(MetadataEpmDocument.from(rootNode.getMetadataEpm()));
            child.setMetadata(MetadataDocument.from(rootNode.getMetadata()));
        } catch( JsonProcessingException e ) {
            e.printStackTrace();
        }

        child.setResources(Stream.of(
                rootNode.getResResource(),
                rootNode.getContextResource(),
                rootNode.getResource())
                    .filter(Objects::nonNull)
                    .map(r -> new ResourceDocument(r, rootNode))
                    .collect(Collectors.toList()));

        this.nodes = Collections.singletonList(child);
    }

    @JsonGetter("code")
    @JacksonXmlProperty(isAttribute = true)
    public String getCode() {
        return code;
    }

    @JsonGetter("version")
    public int getVersion() {
        return version;
    }

    @JsonGetter("id")
    @JacksonXmlProperty(isAttribute = true)
    public UUID getId() {
        return id;
    }

    @JsonGetter("gid")
    @JacksonXmlProperty(isAttribute = true)
    public Long getGid() {
        return gid;
    }

    @JsonGetter("root_node_id")
    @JacksonXmlProperty(isAttribute = true, localName = "root_node_id")
    public UUID getRootNodeId() {
        return rootNodeId;
    }

    @JsonGetter("owner")
    @JacksonXmlProperty(isAttribute = true)
    public boolean getOwner() {
        return owner;
    }

    @JsonGetter("ownerid")
    @JacksonXmlProperty(isAttribute = true, localName = "ownerid")
    public Long getOwnerId() {
        return ownerId;
    }

    @JsonGetter("modified")
    @JacksonXmlProperty(isAttribute = true, localName = "modified")
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss.S")
    public Date getModifDate() {
        return modifDate;
    }

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "asmRoot")
    public List<NodeDocument> getNodes() {
        return nodes;
    }

    @JacksonXmlProperty(localName = "asmRoot")
    public void setNodes(NodeDocument node) {
        node.type = "asmRoot";
        this.nodes.add( node );
    }

    public void setNodes(List<NodeDocument> nodes) {
        this.nodes = nodes;
    }
}
