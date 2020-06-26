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
package au.gov.asd.tac.constellation.visual.vulkan;

import static au.gov.asd.tac.constellation.visual.vulkan.CVKUtils.checkVKret;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

public class CVKBuffer {
    final static int COPY_SIZE = 4096; //candidate for profiling
    
    //TODO_TT: how do free these in Java, finalize() is deprecated
    protected LongBuffer pBuffer = BufferUtils.createLongBuffer(1);
    protected LongBuffer pBufferMemory = BufferUtils.createLongBuffer(1);
    protected CVKDevice cvkDevice = null;
    protected long bufferSize = 0;
    
    protected CVKBuffer() { }
    
    public long GetBufferHandle() { return pBuffer.get(0); }
    public long GetMemoryBufferHandle() { return pBufferMemory.get(0); }
    
    public void Put(ByteBuffer pBytes, int offset, int size) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer data = stack.mallocPointer(1);
            vkMapMemory(cvkDevice.GetDevice(), GetMemoryBufferHandle(), offset, size, 0, data); //arg 5 is flags
            {
                ByteBuffer dest = data.getByteBuffer(0, (int)size);
                pBytes.limit((int)size);
                dest.put(pBytes);
                pBytes.limit(pBytes.capacity()).rewind();
            }
            vkUnmapMemory(cvkDevice.GetDevice(), GetMemoryBufferHandle());
        }
    }  
    
    /**
     * java.nio.ByteBuffer and their like are unpooled heap buffers unlike DirectByteBuffer
     * and HeapByteBuffer.  The pooled buffers are zeroed by the JVM, the nio buffers on the
     * other hand must be explicitly zeroed.
     */
    public void ZeroMemory() {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer data = stack.mallocPointer(1);
            vkMapMemory(cvkDevice.GetDevice(), GetMemoryBufferHandle(), 0, bufferSize, 0, data);
            {                
                ByteBuffer dest = data.getByteBuffer(0, (int)bufferSize);
                BufferUtils.zeroBuffer(dest);
//                long written = 0;
//                byte zeroes[] = new byte[COPY_SIZE];
//                while (written < bufferSize) {
//                    if ((written + COPY_SIZE) <= bufferSize) {
//                        // The second argument is the offset into the source array not the destination.  The
//                        // nio buffer types walk their position with each put so we don't need to specify an
//                        // destination offset.
//                        dest.put(zeroes, 0, COPY_SIZE);
//                        written += COPY_SIZE;
//                    } else {
//                        long remaining = bufferSize - written;
//                        dest.put(zeroes, 0, (int)remaining);
//                        written += remaining;                        
//                    }
//                }
            }
            vkUnmapMemory(cvkDevice.GetDevice(), GetMemoryBufferHandle());
        }
    }
    
    public void Destroy() {
        if (pBuffer.get(0) != VK_NULL_HANDLE) {
            vkDestroyBuffer(cvkDevice.GetDevice(), pBuffer.get(0), null);
            pBuffer.put(0, VK_NULL_HANDLE);
        }
        if (pBufferMemory.get(0) != VK_NULL_HANDLE) {
            vkFreeMemory(cvkDevice.GetDevice(), pBufferMemory.get(0), null);
            pBufferMemory.put(0, VK_NULL_HANDLE);
        }        
    }
    
    @Override
    public void finalize() throws Throwable {
        Destroy();
        super.finalize();
    }
    
    /**
     * Factory creation method for CVKBuffers
     * 
     * @param cvkDevice
     * @param size
     * @param usage
     * @param properties
     * @return
     */
    public static CVKBuffer Create( CVKDevice cvkDevice,
                                    long size, 
                                    int usage, 
                                    int properties) {
        assert(cvkDevice != null);
        assert(cvkDevice.GetDevice() != null);
        
        int ret;
        CVKBuffer cvkBuffer = new CVKBuffer();      
        try(MemoryStack stack = stackPush()) {
            cvkBuffer.bufferSize = size;
            cvkBuffer.cvkDevice = cvkDevice;
            
            // Creating a buffer doesn't actually back it with memory.  Thanks Vulkan.
            VkBufferCreateInfo vkBufferInfo = VkBufferCreateInfo.callocStack(stack);
            vkBufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
            vkBufferInfo.size(size);
            vkBufferInfo.usage(usage);
            
            // This refers to sharing across device queues and seeing as we only have one queue...
            vkBufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
           
            // Create the buffer, it isn't memory backed yet so not terribly useful
            ret = vkCreateBuffer(cvkDevice.GetDevice(), 
                                 vkBufferInfo, 
                                 null, //alloc callbacks
                                 cvkBuffer.pBuffer);
            checkVKret(ret);
            
            // Calculate memory requirements based on the info we proved to the bufferInfo struct
            VkMemoryRequirements vkMemRequirements = VkMemoryRequirements.mallocStack(stack);
            vkGetBufferMemoryRequirements(cvkDevice.GetDevice(), cvkBuffer.pBuffer.get(0), vkMemRequirements);

            // Allocation info struct, type index needs a little logic as types can be mapped differently between GPUs
            VkMemoryAllocateInfo vkAllocInfo = VkMemoryAllocateInfo.callocStack(stack);
            vkAllocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            vkAllocInfo.allocationSize(vkMemRequirements.size());
            vkAllocInfo.memoryTypeIndex(cvkDevice.GetMemoryType(vkMemRequirements.memoryTypeBits(), properties));

            // Allocate the memory needed for the buffer (still needs to be bound)
            ret = vkAllocateMemory(cvkDevice.GetDevice(), vkAllocInfo, null, cvkBuffer.pBufferMemory);
            checkVKret(ret);

            // We have a pen, we have a apple, we have a pineapple...err bind the buffer to its memory
            ret = vkBindBufferMemory(cvkDevice.GetDevice(), 
                                     cvkBuffer.pBuffer.get(0), 
                                     cvkBuffer.pBufferMemory.get(0), 
                                     0); //this memory exists only for this buffer, so no offset
            checkVKret(ret);
            
            return cvkBuffer;
        }
    }
}
