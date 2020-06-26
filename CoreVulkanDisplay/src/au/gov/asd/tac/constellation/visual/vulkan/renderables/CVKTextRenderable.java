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
package au.gov.asd.tac.constellation.visual.vulkan.renderables;

import au.gov.asd.tac.constellation.utilities.graphics.Matrix44f;
import au.gov.asd.tac.constellation.visual.AutoDrawable;
import au.gov.asd.tac.constellation.visual.vulkan.CVKDevice;
import au.gov.asd.tac.constellation.visual.vulkan.CVKFrame;
import au.gov.asd.tac.constellation.visual.vulkan.CVKRenderer;
import au.gov.asd.tac.constellation.visual.vulkan.CVKSwapChain;
import static au.gov.asd.tac.constellation.visual.vulkan.CVKUtils.VkSucceeded;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferInheritanceInfo;

public class CVKTextRenderable implements CVKRenderable {
    public VkCommandBuffer commandBuffer;
    public long graphicsPipeline;
        
    @Override
    public long GetGraphicsPipeline(){return 0; }
    
    @Override
    public int GetVertexCount(){return 0; }
    @Override
    public VkCommandBuffer GetCommandBuffer(int index){return commandBuffer; }
    @Override
    public int getPriority() { if (true) throw new UnsupportedOperationException(""); else return 0; }
    @Override
    public void dispose(final AutoDrawable drawable) { throw new UnsupportedOperationException("Not yet implemented"); }
    @Override
    public void init(final AutoDrawable drawable) { throw new UnsupportedOperationException("Not yet implemented"); }
    @Override
    public void reshape(final int x, final int y, final int width, final int height) { throw new UnsupportedOperationException("Not yet implemented"); }
    @Override
    public void update(final AutoDrawable drawable) { throw new UnsupportedOperationException("Not yet implemented"); }
    @Override
    public void display(final AutoDrawable drawable, final Matrix44f pMatrix) { throw new UnsupportedOperationException("Not yet implemented"); }
   
    @Override
    public int RecordCommandBuffer(CVKDevice cvkDevice, CVKSwapChain cvkSwapChain, VkCommandBufferInheritanceInfo inheritanceInfo, int index) {
        return 0;
    }
    @Override
    public boolean IsDirty(){return true; }
    
    @Override 
    public int InitCommandBuffer(CVKDevice cvkDevice, CVKSwapChain cvkSwapChain){
        int ret = VK_SUCCESS;
        
        
        return ret;
    }
    
    //@Override    
//    public int LoadShaders(CVKDevice cvkDevice) {
//        int ret = VK_SUCCESS;
//        return ret;
//    }
    
    public int CreatePipeline(CVKDevice cvkDevice, CVKSwapChain cvkSwapChain) {
        int ret = VK_SUCCESS;
        try (MemoryStack stack = stackPush()) {
            
        }
        return ret;
    }
    
    
    public int DestroyPipeline(CVKDevice cvkDevice, CVKSwapChain cvkSwapChain) {
        int ret = VK_SUCCESS;
        try (MemoryStack stack = stackPush()) {
            
        }
        return ret;
    }
    
    
    @Override
    public int SwapChainRezied(CVKDevice cvkDevice, CVKSwapChain cvkSwapChain) {
        int ret = DestroyPipeline(cvkDevice, cvkSwapChain);
        if (VkSucceeded(ret)) {
            ret = CreatePipeline(cvkDevice, cvkSwapChain);
        }
        return ret;
    }   
    
    @Override
    public int DisplayUpdate(CVKDevice cvkDevice, CVKSwapChain cvkSwapChain, int frameIndex) {
        return VK_SUCCESS;
    }
    
    @Override
    public void IncrementDescriptorTypeRequirements(int descriptorTypeCounts[]) {}
    
    @Override
    public void Display(MemoryStack stack, CVKFrame frame, CVKRenderer cvkRenderer, CVKDevice cvkDevice, CVKSwapChain cvkSwapChain, int frameIndex) {}
}