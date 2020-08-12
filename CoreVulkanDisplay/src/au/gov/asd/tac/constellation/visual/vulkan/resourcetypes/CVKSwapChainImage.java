/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package au.gov.asd.tac.constellation.visual.vulkan.resourcetypes;

import au.gov.asd.tac.constellation.visual.vulkan.CVKDevice;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

/**
 * SwapChainImage
 * 
 */
public class CVKSwapChainImage extends CVKImage {
    private CVKSwapChainImage() {}
    
    public static CVKSwapChainImage Initialise(CVKDevice cvkDevice,
                                 long imageHandle) {
        CVKSwapChainImage cvkImage   = new CVKSwapChainImage();
        cvkImage.cvkDevice  = cvkDevice;
        cvkImage.width      = 0;
        cvkImage.height     = 0;
        cvkImage.layers     = 1;
        cvkImage.format     = cvkDevice.GetSurfaceFormat().Value();
        //cvkImage.tiling     = tiling;
        cvkImage.usage      = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT; 
       // cvkImage.properties = properties;
        cvkImage.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;        
        cvkImage.imageType  = VK_IMAGE_TYPE_2D;
        cvkImage.viewType   = VK_IMAGE_VIEW_TYPE_2D;
        cvkImage.layout     = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        cvkImage.pImage.put(0, imageHandle);
        
        return cvkImage;
    }
    
    
    @Override
    public void Destroy() {
        // Don't call super! 
        // Don't destroy the image handles as the swapchain objects owns their memory
        DestroyImageView();
    }
    
    public void ResetImageHandle() {
        pImage.put(0, VK_NULL_HANDLE);
    }
    
    public void SetExtent(int width, int height) {
        this.width = width;
        this.height = height;
    }
}
