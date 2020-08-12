/*
 * Copyright 2010-2020 Australian Signals Directorate
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.gov.asd.tac.constellation.graph.interaction.visual.renderables;

import au.gov.asd.tac.constellation.graph.interaction.framework.HitState;
import au.gov.asd.tac.constellation.graph.interaction.framework.HitState.HitType;
import au.gov.asd.tac.constellation.visual.vulkan.CVKDescriptorPool.CVKDescriptorPoolRequirements;
import au.gov.asd.tac.constellation.visual.vulkan.CVKDevice;
import au.gov.asd.tac.constellation.visual.vulkan.CVKVisualProcessor;
import au.gov.asd.tac.constellation.visual.vulkan.renderables.CVKRenderable;
import au.gov.asd.tac.constellation.visual.vulkan.resourcetypes.CVKCommandBuffer;
import au.gov.asd.tac.constellation.visual.vulkan.resourcetypes.CVKImage;
import static au.gov.asd.tac.constellation.visual.vulkan.utils.CVKGraphLogger.CVKLOGGER;
import static au.gov.asd.tac.constellation.visual.vulkan.utils.CVKMissingEnums.VkFormat.VK_FORMAT_NONE;
import static au.gov.asd.tac.constellation.visual.vulkan.utils.CVKUtils.CVKAssert;
import static au.gov.asd.tac.constellation.visual.vulkan.utils.CVKUtils.VkFailed;
import static au.gov.asd.tac.constellation.visual.vulkan.utils.CVKUtils.VkSucceeded;
import java.nio.LongBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdExecuteCommands;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkCreateRenderPass;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferInheritanceInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkOffset2D;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;


/**
 * This lives in the Core Interactive Graph project so it can import HitTestRequest and
 * HitState.  If it lived in Core Display Vulkan importing those types would require
 * Core Display Vulkan to be dependent on Core Interactive Graph which would be a 
 * circular dependency.
 * 
 * 
 * Steps
 * 1. Create an image the same size as the viewport
 * 2. Create a framebuffer based off that image
 * 3. 
 */
public class CVKHitTester extends CVKRenderable {

    private HitTestRequest hitTestRequest;
    private final BlockingDeque<HitTestRequest> requestQueue = new LinkedBlockingDeque<>();
    private final Queue<Queue<HitState>> notificationQueues = new LinkedList<>();
    private boolean needsDisplayUpdate = true;
    private CVKImage cvkImage = null;
    private CVKImage cvkDepthImage = null;
    private Long vkFrameBufferHandle = null;
    private CVKCommandBuffer commandBuffer = null;
    // TODO Use swapchain image format?
    //private int colorFormat = VK_FORMAT_R8G8B8A8_UINT;

    // ========================> Static init <======================== \\
    
    
    
    // ========================> Lifetime <======================== \\
    
    public CVKHitTester(CVKVisualProcessor parent) {
        this.parent = parent;
    }
    
    @Override
    public int Initialise(CVKDevice cvkDevice) { 
        this.cvkDevice = cvkDevice;
             
        
        return VK_SUCCESS;
    }
    
    @Override
    public void Destroy() {
        DestroyFrameBuffer();
        DestroyImage();
        DestroyCommandBuffer();
        
        CVKAssert(vkFrameBufferHandle == null);
        CVKAssert(cvkImage == null);
        CVKAssert(commandBuffer == null);
        
    	//vkDestroyFramebuffer( cvkDevice, framebufferSource, nullptr );
	//vkDestroyImageView( cvkDevice, imageSourceView, nullptr );
        //cvkImage.Destroy();
	//vkFreeMemory( cvkDevice, memorySource, nullptr );
	//vkDestroyImage( cvkDevice, imageSource, nullptr );
    }
    
    
    // ========================> Swap chain <======================== \\    
    
    @Override
    protected int DestroySwapChainResources() { return VK_SUCCESS; }
    
    private int CreateSwapChainResources() {
        parent.VerifyInRenderThread();
        CVKAssert(cvkSwapChain != null);
        int ret = VK_SUCCESS;

                
        // We only need to recreate these resources if the number of images in 
        // the swapchain changes or if this is the first call after the initial
        // swapchain is created.
        if (swapChainImageCountChanged) {

//            ret = CreateRenderPass();
//            if (VkFailed(ret)) { return ret; }   

            ret = CreateImages();
            if (VkFailed(ret)) { return ret; }            

            ret = CreateFrameBuffer();
            if (VkFailed(ret)) { return ret; }

             ret = CreateCommandBuffer();
            if (VkFailed(ret)) { return ret; }
            
        } else {
            // This is the resize path, image count is unchanged.       
            //UpdatePushConstants();
        }
        
        swapChainResourcesDirty = false;
        swapChainImageCountChanged = false;
        
        return ret;
    } 
    
    
    // ========================> Image <======================== \\
    
    private int CreateImages() {
        CVKAssert(cvkSwapChain != null);
        CVKAssert(cvkSwapChain.GetDepthFormat() != VK_FORMAT_UNDEFINED);
        
        int ret = VK_SUCCESS;
   
        try (MemoryStack stack = stackPush()) {
            
            int textureWidth = cvkSwapChain.GetWidth();
            int textureHeight = cvkSwapChain.GetHeight();
            int requiredLayers = 1;
                        
            // Create destination color image            
            cvkImage = CVKImage.Create(cvkDevice, 
                                            textureWidth, 
                                            textureHeight, 
                                            requiredLayers, 
                                            //colorFormat, // Format TODO Not sure what the format should be - look at GL version
                                            cvkSwapChain.GetColorFormat(),
                                            VK_IMAGE_VIEW_TYPE_2D,
                                            VK_IMAGE_TILING_LINEAR, // Tiling
                                            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT, // TODO Usage 
                                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT , // TODO - properties?
                                            VK_IMAGE_ASPECT_COLOR_BIT);  // TODO - aspect mask
            
            if (cvkImage == null) {
                return 1;
            }
            
            // Create depth image 
            cvkDepthImage = CVKImage.Create(cvkDevice, 
                                            textureWidth, 
                                            textureHeight, 
                                            requiredLayers, 
                                            cvkSwapChain.GetDepthFormat(), // Format TODO Not sure what the format should be - look at GL version
                                            VK_IMAGE_VIEW_TYPE_2D,
                                            VK_IMAGE_TILING_OPTIMAL, // Tiling or VK_IMAGE_TILING_OPTIMAL
                                            VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, // TODO Usage 
                                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, // TODO - properties?
                                            VK_IMAGE_ASPECT_DEPTH_BIT);  // TODO - aspect mask
            if (cvkDepthImage == null) {
                return 1;
            }
//		image.format = fbDepthFormat;
//		image.usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
//
//		VK_CHECK_RESULT(vkCreateImage(device, &image, nullptr, &offscreenPass.depth.image));
//		vkGetImageMemoryRequirements(device, offscreenPass.depth.image, &memReqs);
//		memAlloc.allocationSize = memReqs.size;
//		memAlloc.memoryTypeIndex = vulkanDevice->getMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
//		VK_CHECK_RESULT(vkAllocateMemory(device, &memAlloc, nullptr, &offscreenPass.depth.mem));
//		VK_CHECK_RESULT(vkBindImageMemory(device, offscreenPass.depth.image, offscreenPass.depth.mem, 0));
//
//		VkImageViewCreateInfo depthStencilView = vks::initializers::imageViewCreateInfo();
//		depthStencilView.viewType = VK_IMAGE_VIEW_TYPE_2D;
//		depthStencilView.format = fbDepthFormat;
//		depthStencilView.flags = 0;
//		depthStencilView.subresourceRange = {};
//		depthStencilView.subresourceRange.aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT;
//		depthStencilView.subresourceRange.baseMipLevel = 0;
//		depthStencilView.subresourceRange.levelCount = 1;
//		depthStencilView.subresourceRange.baseArrayLayer = 0;
//		depthStencilView.subresourceRange.layerCount = 1;
//		depthStencilView.image = offscreenPass.depth.image;
//		VK_CHECK_RESULT(vkCreateImageView(device, &depthStencilView, nullptr, &offscreenPass.depth.view));
            
        }
        
//      VkImage imageSource;
//	VkImageCreateInfo isci{ VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, 
//                nullptr,                    // pNext
//                VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT, // crete flags
//                VK_IMAGE_TYPE_2D,           // image type
//                VK_FORMAT_R8G8B8A8_UINT,    // format
//                {width,height, 1},          // extent
//                1, 1,                       // miplevels, arrayLayers
//                VK_SAMPLE_COUNT_1_BIT,      // Samples
//                VK_IMAGE_TILING_LINEAR,     // tiling
//                VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT, // usage
//                VK_SHARING_MODE_EXCLUSIVE,  // sharing mode
//                1,                          // queue family index count    
//                &queueFamily };             // queue family indices
//	VkResult errorCode = vkCreateImage( device, &isci, nullptr, &imageSource ); RESULT_HANDLER( errorCode, "vkCreateImage" );
//
//	VkMemoryRequirements ismr;
//	vkGetImageMemoryRequirements( device, imageSource, &ismr );
//
//	uint32_t memoryType = 0; bool found = false;
//	for( uint32_t i = 0; i < 32; ++i ){
//		if(  ( ismr.memoryTypeBits & (0x1 << i) )  &&  physicalDeviceMemoryProperties.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT  ){
//				memoryType = i; found = true; break;
//		}
//	}
//	if( !found ) throw "Can't find compatible mappable memory for image";
//
//	VkDeviceMemory memorySource;
//	VkMemoryAllocateInfo memoryInfo{ VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, ismr.size, memoryType };
//	ret = vkAllocateMemory( cvkDevice, &memoryInfo, nullptr, &memorySource ); RESULT_HANDLER( errorCode, "vkAllocateMemory" );
//	ret = vkBindImageMemory( cvkDevice, imageSource, memorySource, 0 ); RESULT_HANDLER( errorCode, "vkBindImageMemory" );
//
//	VkImageView imageSourceView;
//	VkImageViewCreateInfo isvci{  VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO, 
//                nullptr, 
//                0, 
//                imageSource, 
//                VK_IMAGE_VIEW_TYPE_2D, 
//                VK_FORMAT_R8G8B8A8_UNORM,  // TODO This is different to image format
////	        {   VK_COMPONENT_SWIZZLE_IDENTITY, 
//                    VK_COMPONENT_SWIZZLE_IDENTITY, 
//                    VK_COMPONENT_SWIZZLE_IDENTITY, 
//                    VK_COMPONENT_SWIZZLE_IDENTITY },
////	        { VK_IMAGE_ASPECT_COLOR_BIT, 
//                            0, 
//                    VK_REMAINING_MIP_LEVELS,   // TODO Is this needed?
//                            0, 
//                    VK_REMAINING_ARRAY_LAYERS }  };  // TODO Is this needed?
//	ret = vkCreateImageView( cvkDevice, &isvci, nullptr, &imageSourceView ); RESULT_HANDLER( errorCode, "vkCreateImageView" );

        return ret;
    }
    
    private void DestroyImage() {
        if (cvkImage != null) {
            cvkImage.Destroy();
            cvkImage = null;
        }
    }
    
    
    // ========================> Frame buffer <======================== \\
    
    private int CreateFrameBuffer() {
        int ret = VK_SUCCESS;
               
        try(MemoryStack stack = stackPush()) {
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.callocStack(stack);
            framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
            framebufferInfo.renderPass(cvkSwapChain.GetOffscreenRenderPassHandle());
            framebufferInfo.width(cvkSwapChain.GetWidth());
            framebufferInfo.height(cvkSwapChain.GetHeight());
            framebufferInfo.layers(1);

            LongBuffer attachments = stack.mallocLong(2);
            LongBuffer pFramebuffer = stack.mallocLong(1);

            attachments.put(0, cvkImage.GetImageViewHandle());
            // TODO Depth image ?
            attachments.put(1, cvkDepthImage.GetImageViewHandle());
            framebufferInfo.pAttachments(attachments);
            ret = vkCreateFramebuffer(cvkDevice.GetDevice(), 
                                      framebufferInfo, 
                                      null, //allocation callbacks
                                      pFramebuffer);
            if (VkFailed(ret)) { return ret; }
            
            vkFrameBufferHandle = pFramebuffer.get(0);

        }
        return ret;

    }
    
    private void DestroyFrameBuffer() {
        if (vkFrameBufferHandle != null) {
            vkDestroyFramebuffer(cvkDevice.GetDevice(), vkFrameBufferHandle, null);
            vkFrameBufferHandle = null;
            CVKLOGGER.info(String.format("Destroyed frame buffer for HitTester"));
        }
    }  
    
    
    // ========================> Vertex buffers <======================== \\    
    
    @Override
    public int GetVertexCount() { return 0; }
    
    
    // ========================> Command buffers <======================== \\
    
    private int CreateCommandBuffer() {       
        CVKAssert(cvkDevice != null);
        
        int ret = VK_SUCCESS;
             
        commandBuffer = CVKCommandBuffer.Create(cvkDevice, VK_COMMAND_BUFFER_LEVEL_PRIMARY);
        commandBuffer.DEBUGNAME = String.format("CVKHitTester");
        
        CVKLOGGER.log(Level.INFO, "Init Command Buffer - HitTester");
        
        return ret;
    }
    
    @Override
    public VkCommandBuffer GetCommandBuffer(int imageIndex) { return commandBuffer.GetVKCommandBuffer(); }
    
    @Override
    public int RecordCommandBuffer(VkCommandBufferInheritanceInfo inheritanceInfo, int index) { 
        return VK_SUCCESS;
    }
        
    private void DestroyCommandBuffer() {
        if (commandBuffer != null) {
            commandBuffer.Destroy();
            commandBuffer = null;
        }
    }
    
    
    // ========================> RenderPass <======================== \\
//    private int CreateRenderPass() {
//        int ret = VK_SUCCESS;
//
//        // Create the actual renderpass
//        VkRenderPassCreateInfo renderPassInfo = VK_NULL_HANDLE;
//        renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
//        renderPassInfo.attachmentCount = static_cast<uint32_t>(attchmentDescriptions.size());
//        renderPassInfo.pAttachments = attchmentDescriptions.data();
//        renderPassInfo.subpassCount = 1;
//        renderPassInfo.pSubpasses = &subpassDescription;
//        renderPassInfo.dependencyCount = static_cast<uint32_t>(dependencies.size());
//        renderPassInfo.pDependencies = dependencies.data();
//
//        ret = vkCreateRenderPass(cvkDevice.GetDevice(), renderPassInfo, null, hOffscreenRenderPassHandle);
//        if (vk_failed(ret) ){ return ret; }
//    }
    
//    private int CreateRenderPass() {
//        CVKAssert(cvkDevice.GetDevice() != null);
//        CVKAssert(cvkDevice.GetSurfaceFormat() != VK_FORMAT_NONE);
//        
//        int ret;      
//        
//        try(MemoryStack stack = stackPush()) {
//        
//            // 0: colour, 1: depth
//            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.callocStack(2, stack);
//            VkAttachmentReference.Buffer attachmentRefs = VkAttachmentReference.callocStack(2, stack);        
//
//                  // Color attachment
////		attchmentDescriptions[0].format = FB_COLOR_FORMAT;
////		attchmentDescriptions[0].samples = VK_SAMPLE_COUNT_1_BIT;
////		attchmentDescriptions[0].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
////		attchmentDescriptions[0].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
////		attchmentDescriptions[0].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
////		attchmentDescriptions[0].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
////		attchmentDescriptions[0].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
////		attchmentDescriptions[0].finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
//
//            // Colour attachment
//            VkAttachmentDescription colorAttachment = attachments.get(0);
//            colorAttachment.format(colorFormat);
//            colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
//            colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
//            colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE);
//            colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
//            colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
//
//            // These are the states of our display images at the start and end of this pass
//            colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
//            colorAttachment.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
//
//            VkAttachmentReference colorAttachmentRef = attachmentRefs.get(0);
//            colorAttachmentRef.attachment(0);
//            colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
//
//            //		// Depth attachment
////		attchmentDescriptions[1].format = fbDepthFormat;
////		attchmentDescriptions[1].samples = VK_SAMPLE_COUNT_1_BIT;
////		attchmentDescriptions[1].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
////		attchmentDescriptions[1].storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
////		attchmentDescriptions[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
////		attchmentDescriptions[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
////		attchmentDescriptions[1].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
////		attchmentDescriptions[1].finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
////
////		VkAttachmentReference colorReference = { 0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL };
////		VkAttachmentReference depthReference = { 1, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL };
//            // Depth attachment
//            VkAttachmentDescription depthAttachment = attachments.get(1);
//            depthAttachment.format(cvkSwapChain.GetDepthFormat());
//            depthAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
//            depthAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
//            depthAttachment.storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
//            depthAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
//            depthAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
//            depthAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);//VK_IMAGE_LAYOUT_UNDEFINED);
//            depthAttachment.finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL); //VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
//
//            VkAttachmentReference depthAttachmentRef = attachmentRefs.get(1);
//            depthAttachmentRef.attachment(1);
//            depthAttachmentRef.layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);  
//
//            VkSubpassDescription.Buffer subpass = VkSubpassDescription.callocStack(1, stack);
//            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
//            subpass.colorAttachmentCount(1);
//            // This bit of hackery is because pColorAttachments is a buffer of multiple references, whereas pDepthStencilAttachment is singular
//            subpass.pColorAttachments(VkAttachmentReference.callocStack(1, stack).put(0, colorAttachmentRef)); 
//            subpass.pDepthStencilAttachment(depthAttachmentRef);
//
//            VkSubpassDependency.Buffer dependency = VkSubpassDependency.callocStack(1, stack);
//            dependency.srcSubpass(VK_SUBPASS_EXTERNAL);
//            dependency.dstSubpass(0);
//            dependency.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
//            dependency.srcAccessMask(0);
//            dependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
//            dependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
//
//            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.callocStack(stack);
//            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
//            renderPassInfo.pAttachments(attachments);
//            renderPassInfo.pSubpasses(subpass);
//            renderPassInfo.pDependencies(dependency);
//
//            LongBuffer pRenderPass = stack.mallocLong(1);
//            ret = vkCreateRenderPass(cvkDevice.GetDevice(),
//                                     renderPassInfo, 
//                                     null, //allocation callbacks
//                                     pRenderPass);
//            if (VkSucceeded(ret)) {
//                hOffscreenRenderPassHandle = pRenderPass.get(0);        
//            }
//        }
//        return ret;
//    } 
    
    
    // ========================> Descriptors <======================== \\
    
    @Override
    public int DestroyDescriptorPoolResources() { return VK_SUCCESS; }     
      
    @Override
    public void IncrementDescriptorTypeRequirements(CVKDescriptorPoolRequirements reqs, CVKDescriptorPoolRequirements perImageReqs) {}       
    
    
    // ========================> Display <======================== \\
    
    @Override
    public boolean NeedsDisplayUpdate() { 
        return needsDisplayUpdate; 
    }
     
    @Override
    public int DisplayUpdate() {
        int ret = VK_SUCCESS;
        
        if (swapChainResourcesDirty) {
            ret = CreateSwapChainResources();
            if (VkFailed(ret)) { return ret; }
        }
        
        
        // TODO Hydra: Need to reset the needsDisplayUpdate flag in here    
        if (requestQueue != null && !requestQueue.isEmpty()) {
            requestQueue.forEach(request -> notificationQueues.add(request.getNotificationQueue()));
            hitTestRequest = requestQueue.getLast();
            requestQueue.clear();
        }
        
        if (!notificationQueues.isEmpty()) {
            final int x = hitTestRequest.getX();
            final int y = hitTestRequest.getY();

            final HitState hitState = hitTestRequest.getHitState();
            hitState.setCurrentHitId(-1);
            hitState.setCurrentHitType(HitType.NO_ELEMENT);
            if (hitTestRequest.getFollowUpOperation() != null) {
                hitTestRequest.getFollowUpOperation().accept(hitState);
            }
            synchronized (this.notificationQueues) {
                while (!notificationQueues.isEmpty()) {
                    final Queue<HitState> queue = notificationQueues.remove();
                    if (queue != null) {
                        queue.add(hitState);
                    }
                }
            }
        }
        return ret;
    }  

    @Override
    public int OffscreenRender(List<CVKRenderable> hitTestRenderables) {
        parent.VerifyInRenderThread();
        
        CVKAssert(cvkDevice.GetDevice() != null);
        CVKAssert(cvkDevice.GetCommandPoolHandle() != VK_NULL_HANDLE);
        CVKAssert(cvkSwapChain != null);
                
        int ret = VK_SUCCESS;
        
        try (MemoryStack stack = stackPush()) {
            
            //VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
            //beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            
            ret = commandBuffer.Begin(VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT);
            if (VkFailed(ret)) { return ret; }
            
            // TODO: what are the flags?
            //commandBuffer.Begin(VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT);
//            VkImageMemoryBarrier predrawBarrier{  VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, 
//nullptr,          // Next
//0,                // Src AcessMask
//VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,         // Dest Access Mask
//VK_IMAGE_LAYOUT_UNDEFINED,                        // old layout
//VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,         // new layout
//VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED, imageSource,
// { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 }  };   // SubresourceRange
//            vkCmdPipelineBarrier( renderCommandBuffer,
//VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 
//VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, 
//0, 0, 
//nullptr, 0, 
//nullptr, 1, &predrawBarrier );
            
            // Pre Draw Barrier
            commandBuffer.pipelineImageMemoryBarrierCmd(cvkImage.GetImageHandle(), 
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,    // Old/New Layout
                    0, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,                                // Src/Dst Access mask
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,  // Src/Dst Stage mask
                    0, 1);                                                                  // baseMipLevel/mipLevelCount
            
//            VkImageSubresourceRange subresource = VkImageSubresourceRange.calloc();
//            subresource.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT );
//            
//            VkImageMemoryBarrier.Buffer vkBarrier = VkImageMemoryBarrier.callocStack(1, stack);
//            vkBarrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
//            vkBarrier.oldLayout(VK_IMAGE_LAYOUT_UNDEFINED);
//            vkBarrier.newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
//            vkBarrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
//            vkBarrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
//            vkBarrier.srcAccessMask(0);
//            vkBarrier.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT );
//            vkBarrier.subresourceRange(subresource);
//            vkBarrier.image(cvkImage.GetImageHandle());
//         
//            vkCmdPipelineBarrier(commandBuffer.GetVKCommandBuffer(),
//                                     VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
//                                     VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
//                                     0,                 // dependency flags
//                                     null,              // memory barriers
//                                     null,              // buffer memory barriers
//                                     vkBarrier);        // image memory barriers     
//            
            
            // Clear colour to black
            VkClearValue.Buffer clearValues = VkClearValue.callocStack(2, stack);
            clearValues.color().float32(stack.floats(0f, 1.0f, 0.5f, 1.0f));
            clearValues.get(1).depthStencil().set(1.0f, 0);

            VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.callocStack(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
            renderPassInfo.renderPass(cvkSwapChain.GetOffscreenRenderPassHandle());

            VkRect2D renderArea = VkRect2D.callocStack(stack);
            renderArea.offset(VkOffset2D.callocStack(stack).set(0, 0));
            renderArea.extent(cvkSwapChain.GetExtent());
            renderPassInfo.renderArea(renderArea);       
            renderPassInfo.pClearValues(clearValues);
            renderPassInfo.framebuffer(vkFrameBufferHandle);


            //  TODO - VK_SUBPASS_CONTENTS_INLINE  OR VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS
            vkCmdBeginRenderPass(commandBuffer.GetVKCommandBuffer(), renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
            //commandBuffer.beginRenderPassCmd();

            // Inheritance info for the secondary command buffers (same for all!)
            VkCommandBufferInheritanceInfo inheritanceInfo = VkCommandBufferInheritanceInfo.callocStack(stack);
            inheritanceInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO);
            inheritanceInfo.pNext(0);
            inheritanceInfo.framebuffer(vkFrameBufferHandle);
            inheritanceInfo.renderPass(cvkSwapChain.GetOffscreenRenderPassHandle());
            //inheritanceInfo.subpass(0); // Get the subpass of make it here?
            inheritanceInfo.occlusionQueryEnable(false);
            inheritanceInfo.queryFlags(0);
            inheritanceInfo.pipelineStatistics(0);

                           
	    // Set the dynamic viewport and scissor
            //commandBuffer.viewPortCmd(cvkSwapChain.GetWidth(), cvkSwapChain.GetHeight(), stack);
            //commandBuffer.scissorCmd(cvkDevice.GetCurrentSurfaceExtent(), stack);
            
            // Check flags and render the nodes and connections
            /// Loop through command buffers of hit test objects and record their buffers
            hitTestRenderables.forEach(renderable -> {
            // TODO HYDRA - WIP HIT TESTER
//                if (renderable.GetVertexCount() > 0) {
//                    renderable.RecordOffscreenCommandBuffer(inheritanceInfo, 0);
//                    vkCmdExecuteCommands(commandBuffer.GetVKCommandBuffer(), renderable.GetCommandBuffer(0));
//                }
            });
            
            vkCmdEndRenderPass(commandBuffer.GetVKCommandBuffer());
        
            // Pre Draw Barrier
            commandBuffer.pipelineImageMemoryBarrierCmd(cvkImage.GetImageHandle(), 
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL,//VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL,    // Old/New Layout
                    0, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,                                // Src/Dst Access mask
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, //VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,  // Src/Dst Stage mask
                    0, 1);                                                                  // baseMipLevel/mipLevelCount
//            VkImageMemoryBarrier premapBarrier{  VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, nullptr, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_HOST_READ_BIT | VK_ACCESS_MEMORY_READ_BIT, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL,
//		                                      VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED, imageSource, { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 }  };
//		vkCmdPipelineBarrier( renderCommandBuffer, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr, 0, nullptr, 1, &premapBarrier );
//
            
            // Do we need this?
            vkDeviceWaitIdle(cvkDevice.GetDevice());
            commandBuffer.EndAndSubmit();
            
            // Copy the contents of the image to a buffer
            //vkCmdCopyImageToBuffer
            
            //MapMemory();
        }
        
        
        return ret;
    
    }    
    
     
    private int MapMemory() {
        
        int ret = VK_SUCCESS;
        
        // Test code to write out the image to file
        //ByteBuffer data;
        String fileName = "screenshot.png";
        
        ret = cvkImage.SaveToFile(fileName);
              
//	void* data;
//	ret = vkMapMemory( cvkDevice, memorySource, 0, VK_WHOLE_SIZE, 0, &data ); RESULT_HANDLER( errorCode, "vkMapMemory" );
//	std::ofstream ofs( "out.raw", std::ostream::binary );
//	ofs.write( (char*)data, width * height * 4 );
//	vkUnmapMemory( cvkDevice, memorySource );

        return ret;
    }
        

    // ========================> Tasks <======================== \\
    
    public void queueRequest(final HitTestRequest request) {
        requestQueue.add(request);
        needsDisplayUpdate = true;
    }

}
