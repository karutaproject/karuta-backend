package eportfolium.com.karuta.business.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import eportfolium.com.karuta.business.ServiceTest;
import eportfolium.com.karuta.consumer.repositories.CredentialRepository;
import eportfolium.com.karuta.consumer.repositories.NodeRepository;
import eportfolium.com.karuta.consumer.repositories.ResourceRepository;
import eportfolium.com.karuta.document.ResourceDocument;
import eportfolium.com.karuta.document.ResourceList;
import eportfolium.com.karuta.model.bean.GroupRights;
import eportfolium.com.karuta.model.bean.Node;
import eportfolium.com.karuta.model.bean.Resource;
import eportfolium.com.karuta.model.exception.BusinessException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ServiceTest
public class ResourceManagerTest {
    @SpyBean
    private ResourceManager manager;

    @MockBean
    private ResourceRepository resourceRepository;

    @MockBean
    private CredentialRepository credentialRepository;

    @MockBean
    private NodeRepository nodeRepository;

    @MockBean
    private NodeManager nodeManager;

    @MockBean
    private PortfolioManager portfolioManager;

    @Test
    public void getResource_WithoutProperRights() {
        Long userId = 42L;
        Long groupId = 56L;

        UUID nodeId = UUID.randomUUID();

        doReturn(false)
                .when(manager)
                .hasRight(userId, groupId, nodeId, GroupRights.READ);

        try {
            manager.getResource(nodeId, userId, groupId);
            fail("User must have the right to read to access this method.");
        } catch (BusinessException ignored) { }
    }

    @Test
    public void getResource_WithProperRights() throws BusinessException {
        Long userId = 42L;
        Long groupId = 56L;

        UUID nodeId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        Resource resource = new Resource();
        resource.setId(resourceId);
        resource.setXsiType("foo");

        doReturn(true)
                .when(manager)
                .hasRight(userId, groupId, nodeId, GroupRights.READ);

        doReturn(resource)
                .when(resourceRepository)
                .getResourceByParentNodeUuid(nodeId);

        ResourceDocument resourceDocument = manager.getResource(nodeId, userId, groupId);

        assertEquals(resource.getId(), resourceDocument.getId());
        assertEquals(resource.getXsiType(), resourceDocument.getContent());
        assertEquals(nodeId, resourceDocument.getNodeId());

        verify(resourceRepository).getResourceByParentNodeUuid(nodeId);
    }

    @Test
    public void getResources() {
        Long userId = 42L;
        Long groupId = 74L;

        UUID portfolioId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        Node node = new Node();
        node.setId(nodeId);

        Resource resource = new Resource();
        resource.setNode(node);

        doReturn(Collections.singletonList(resource))
                .when(resourceRepository)
                .getResourcesByPortfolioUUID(portfolioId);

        ResourceList resourceList = manager.getResources(portfolioId, userId, groupId);

        assertEquals(1, resourceList.getResources().size());
        assertEquals(nodeId, resourceList.getResources().get(0).getId());
    }

    @Test
    public void changeResource_WithoutWriteRight() throws JsonProcessingException {
        Long userId = 42L;
        Long groupId = 74L;

        UUID nodeId = UUID.randomUUID();

        doReturn(false)
                .when(manager)
                .hasRight(userId, groupId, nodeId, GroupRights.WRITE);

        try {
            manager.changeResource(nodeId, null, userId, groupId);
            fail("User must have write rights to change the resource.");
        } catch (BusinessException ignored) { }
    }

    @Test
    public void changeResource_WithWriteRight()
            throws JsonProcessingException, BusinessException {
        Long userId = 42L;
        Long groupId = 74L;

        UUID nodeId = UUID.randomUUID();
        Resource resource = new Resource();

        ResourceDocument document = new ResourceDocument();
        document.setCode("foo");

        doReturn(true)
                .when(manager)
                .hasRight(userId, groupId, nodeId, GroupRights.WRITE);

        doReturn(resource)
                .when(resourceRepository)
                .getResourceByParentNodeUuid(nodeId);

        int result = manager.changeResource(nodeId, document, userId, groupId);

        assertEquals(0, result);

        assertEquals(userId, resource.getModifUserId());
        assertEquals(userId, resource.getCredential().getId());
        assertEquals("<code>foo</code>", resource.getContent());

        verify(portfolioManager).updateTimeByNode(nodeId);
        verify(resourceRepository).save(resource);
    }

    @Test
    public void addResource_WithoutWriteRight() {
        Long userId = 42L;
        Long groupId = 74L;

        UUID nodeId = UUID.randomUUID();

        doReturn(false)
                .when(manager)
                .hasRight(userId, groupId, nodeId, GroupRights.WRITE);

        ResourceDocument resourceDocument = new ResourceDocument();

        try {
            manager.addResource(nodeId, resourceDocument, userId, groupId);
            fail("User must have write rights to add a resource.");
        } catch (BusinessException ignored) { }
    }

    @Test
    public void addResource_WithWriteRight() throws BusinessException {
        Long userId = 42L;
        Long groupId = 74L;

        UUID nodeId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        Resource resource = new Resource();
        Node node = new Node();

        doReturn(true)
                .when(manager)
                .hasRight(userId, groupId, nodeId, GroupRights.WRITE);

        doReturn(Optional.of(resource))
                .when(resourceRepository)
                .findById(resourceId);

        doReturn(Optional.of(node))
                .when(nodeRepository)
                .findById(nodeId);

        ResourceDocument resourceDocument = mock(ResourceDocument.class);
        when(resourceDocument.getXsiType()).thenReturn("nodeRes");
        when(resourceDocument.getContent()).thenReturn("foo");
        when(resourceDocument.getId()).thenReturn(resourceId);

        manager.addResource(nodeId, resourceDocument, userId, groupId);

        assertEquals(resourceDocument.getXsiType(), resource.getXsiType());
        assertEquals(resourceDocument.getContent(), resource.getContent());
        assertEquals(userId, resource.getModifUserId());

        assertEquals(resource, node.getResResource());

        verify(resourceRepository).findById(resourceId);
        verify(resourceRepository).save(resource);
        verifyNoMoreInteractions(resourceRepository);

        verify(nodeRepository).findById(nodeId);
        verify(nodeRepository).save(node);
        verifyNoMoreInteractions(nodeRepository);
    }

    @Test
    public void addResource_WithAdminRight() throws BusinessException {
        Long userId = 42L;
        Long groupId = 74L;

        UUID nodeId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        Node node = new Node();
        Resource resource = new Resource();

        doReturn(true)
                .when(credentialRepository)
                .isAdmin(userId);

        doReturn(Optional.of(resource))
                .when(resourceRepository)
                .findById(resourceId);

        doReturn(Optional.of(node))
                .when(nodeRepository)
                .findById(nodeId);

        ResourceDocument resourceDocument = mock(ResourceDocument.class);
        when(resourceDocument.getXsiType()).thenReturn("res");
        when(resourceDocument.getId()).thenReturn(resourceId);

        manager.addResource(nodeId, resourceDocument, userId, groupId);

        assertEquals(resourceDocument.getXsiType(), resource.getXsiType());
        assertEquals(resource, node.getResource());

        verify(resourceRepository).findById(resourceId);
        verify(resourceRepository).save(resource);
        verifyNoMoreInteractions(resourceRepository);

        verify(nodeRepository).findById(nodeId);
        verify(nodeRepository).save(node);
        verifyNoMoreInteractions(nodeRepository);
    }

    @Test
    public void removeResource_WithoutDeleteRight() {
        Long userId = 42L;
        Long groupId = 74L;

        UUID resourceId = UUID.randomUUID();

        doReturn(false)
                .when(manager)
                .hasRight(userId, groupId, resourceId, GroupRights.DELETE);

        try {
            manager.removeResource(resourceId, userId, groupId);
            fail("User must have delete right to delete a resource.");
        } catch (BusinessException ignored) { }

        verifyNoInteractions(resourceRepository);
    }

    @Test
    public void removeResource_WithDeleteRight() throws BusinessException {
        Long userId = 42L;
        Long groupId = 74L;

        UUID resourceId = UUID.randomUUID();

        doReturn(true)
                .when(manager)
                .hasRight(userId, groupId, resourceId, GroupRights.DELETE);

        manager.removeResource(resourceId, userId, groupId);

        verify(resourceRepository).deleteById(resourceId);
    }

    @Test
    public void removeResource_BeingAdmin() throws BusinessException {
        Long userId = 42L;
        Long groupId = 74L;

        UUID resourceId = UUID.randomUUID();

        doReturn(true)
                .when(credentialRepository)
                .isAdmin(userId);

        manager.removeResource(resourceId, userId, groupId);

        verify(resourceRepository).deleteById(resourceId);
    }

    @Test
    public void changeResourceByXsiType_ForNodeRes_WithExistingCode() {
        Long userId = 42L;

        String xsiType = "nodeRes";
        String code = "foo";

        UUID nodeId = UUID.randomUUID();

        ResourceDocument resourceDocument = mock(ResourceDocument.class);
        when(resourceDocument.getCode()).thenReturn(code);

        doReturn(true)
                .when(nodeRepository)
                .isCodeExist(code, nodeId);

        try {
            manager.changeResourceByXsiType(nodeId, xsiType, resourceDocument, userId);
            fail("Method must throw on existing code");
        } catch (BusinessException ignored) { }

        verifyNoInteractions(resourceRepository);
    }

    @Test
    public void changeResourceByXsiType_ForNodeRes_WithFailingUpdate() {
        Long userId = 42L;

        String code = "foo";
        String xsiType = "nodeRes";

        UUID nodeId = UUID.randomUUID();

        doReturn(false)
                .when(nodeRepository)
                .isCodeExist(code, nodeId);

        doReturn(1)
                .when(nodeManager)
                .updateNodeCode(nodeId, code);

        ResourceDocument resourceDocument = mock(ResourceDocument.class);
        when(resourceDocument.getCode()).thenReturn(code);

        try {
            manager.changeResourceByXsiType(nodeId, xsiType, resourceDocument, userId);
            fail("A failing node's code update must throw an error.");
        } catch (BusinessException ignored) { }

        verifyNoInteractions(resourceRepository);
    }

    @Test
    public void changeResourceByXsiType_ForNodeRes() throws BusinessException {
        Long userId = 42L;

        String code = "foo";
        String xsiType = "nodeRes";

        UUID nodeId = UUID.randomUUID();

        Resource resource = new Resource();

        ResourceDocument resourceDocument = mock(ResourceDocument.class);
        when(resourceDocument.getCode()).thenReturn(code);
        when(resourceDocument.getContent()).thenReturn("foo");

        doReturn(resource)
                .when(resourceRepository)
                .getResourceOfResourceByNodeUuid(nodeId);

        manager.changeResourceByXsiType(nodeId, xsiType, resourceDocument, userId);

        assertEquals(userId, resource.getModifUserId());
        assertEquals(resourceDocument.getContent(), resource.getContent());

        verify(resourceRepository).getResourceOfResourceByNodeUuid(nodeId);
        verify(resourceRepository).save(resource);
        verifyNoMoreInteractions(resourceRepository);
    }

    @Test
    public void changeResourceByXsiType_ForContext() throws BusinessException {
        Long userId = 42L;
        UUID nodeId = UUID.randomUUID();
        String xsiType = "context";

        Resource resource = new Resource();

        ResourceDocument resourceDocument = mock(ResourceDocument.class);
        when(resourceDocument.getContent()).thenReturn("foo");

        doReturn(resource)
                .when(resourceRepository)
                .getContextResourceByNodeUuid(nodeId);

        manager.changeResourceByXsiType(nodeId, xsiType, resourceDocument, userId);

        assertEquals(userId, resource.getModifUserId());
        assertEquals(resourceDocument.getContent(), resource.getContent());

        verify(resourceRepository).getContextResourceByNodeUuid(nodeId);
        verify(resourceRepository).save(resource);
        verifyNoMoreInteractions(resourceRepository);
    }

    @Test
    public void changeResourceByXsiType_ForRegular() throws BusinessException {
        Long userId = 42L;
        UUID nodeId = UUID.randomUUID();
        String xsiType = "";

        Resource resource = new Resource();

        ResourceDocument resourceDocument = mock(ResourceDocument.class);
        when(resourceDocument.getContent()).thenReturn("foo");

        doReturn(resource)
                .when(resourceRepository)
                .getResourceByParentNodeUuid(nodeId);

        manager.changeResourceByXsiType(nodeId, xsiType, resourceDocument, userId);

        assertEquals(userId, resource.getModifUserId());
        assertEquals(resourceDocument.getContent(), resource.getContent());

        verify(resourceRepository).getResourceByParentNodeUuid(nodeId);
        verify(resourceRepository).save(resource);
        verifyNoMoreInteractions(resourceRepository);
    }

    @Test
    public void updateResource() {
        UUID id = UUID.randomUUID();

        String xsiType = "foo";
        String content = "bar";

        Long userId = 42L;

        manager.updateResource(id, xsiType, content, userId);

        verify(resourceRepository).deleteById(id);
        verify(resourceRepository).save(any(Resource.class));
        verifyNoMoreInteractions(resourceRepository);
    }
}