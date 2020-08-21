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

import au.gov.asd.tac.constellation.visual.vulkan.renderables.CVKIconTextureAtlas;
import au.gov.asd.tac.constellation.utilities.camera.Camera;
import au.gov.asd.tac.constellation.utilities.camera.Graphics3DUtilities;
import au.gov.asd.tac.constellation.utilities.color.ConstellationColor;
import au.gov.asd.tac.constellation.utilities.graphics.Frustum;
import au.gov.asd.tac.constellation.utilities.graphics.Matrix44f;
import au.gov.asd.tac.constellation.utilities.visual.DrawFlags;
import au.gov.asd.tac.constellation.utilities.visual.VisualAccess;
import au.gov.asd.tac.constellation.utilities.visual.VisualChange;
import au.gov.asd.tac.constellation.utilities.visual.VisualChangeBuilder;
import au.gov.asd.tac.constellation.utilities.visual.VisualOperation;
import au.gov.asd.tac.constellation.utilities.visual.VisualProcessor;
import au.gov.asd.tac.constellation.utilities.visual.VisualProcessor.VisualChangeProcessor;
import au.gov.asd.tac.constellation.utilities.visual.VisualProperty;
import static au.gov.asd.tac.constellation.visual.vulkan.utils.CVKUtils.VkFailed;
import au.gov.asd.tac.constellation.visual.vulkan.renderables.CVKAxesRenderable;
import au.gov.asd.tac.constellation.visual.vulkan.renderables.CVKFPSRenderable;
import au.gov.asd.tac.constellation.visual.vulkan.renderables.CVKHitTester;
import au.gov.asd.tac.constellation.visual.vulkan.renderables.CVKIconsRenderable;
import au.gov.asd.tac.constellation.visual.vulkan.renderables.CVKLabelsRenderable;
import au.gov.asd.tac.constellation.visual.vulkan.renderables.CVKPointsRenderable;
import au.gov.asd.tac.constellation.visual.vulkan.renderables.CVKRenderable;
import au.gov.asd.tac.constellation.visual.vulkan.CVKRenderUpdateTask;
import au.gov.asd.tac.constellation.visual.vulkan.utils.CVKGraphLogger;
import static au.gov.asd.tac.constellation.visual.vulkan.utils.CVKUtils.CVK_DEBUGGING;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;


public class CVKVisualProcessor extends VisualProcessor {

    private static boolean DEBUGGING_POINTS = false;
    
    protected static final Cursor DEFAULT_CURSOR = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
    protected static final Cursor CROSSHAIR_CURSOR = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);  
    
    private static final float FIELD_OF_VIEW = 35;
    private static final float PERSPECTIVE_NEAR = 1;
    private static final float PERSPECTIVE_FAR = 500000;
    
    private final BlockingQueue<CVKRenderUpdateTask> taskQueue = new LinkedBlockingQueue<>();
    private final CVKCanvas cvkCanvas;
    private final Frustum viewFrustum = new Frustum();
    private final Matrix44f projectionMatrix = new Matrix44f();       
    private final CVKIconTextureAtlas cvkIconTextureAtlas;
    protected final CVKHitTester cvkHitTester;
    private final CVKAxesRenderable cvkAxes;
    private final CVKFPSRenderable cvkFPS;
    private final CVKIconsRenderable cvkIcons;
    private final CVKLabelsRenderable cvkLabels;
    private final CVKPointsRenderable cvkPoints;
    private final Matrix44f modelViewMatrix = new Matrix44f();  
    private Camera camera = null;
    private float pixelDensity = 0.0f;    
    private final Thread renderThread;
    protected final CVKGraphLogger cvkLogger;
    
    
    // TODO: how should this work with continuous rendering?
    // we need to somehow not cause the hit tester's NeedsDisplayUpdate to trigger
    // a full redraw.
    private boolean shouldRender = true; 
    
    
    public Matrix44f GetProjectionMatrix() { return projectionMatrix; }
    public CVKIconTextureAtlas GetTextureAtlas() { return cvkIconTextureAtlas; }
    public float GetPixelDensity() { return pixelDensity; }
    public int GetFrameNumber() { return cvkCanvas != null ? cvkCanvas.GetFrameNumber() : -1; }
    public Thread GetRenderThread() { return renderThread; }
    public long GetRenderThreadID() { return renderThread != null ? renderThread.getId() : -1; }
    public CVKGraphLogger GetLogger() { return cvkLogger; }
    
    
    public CVKVisualProcessor(final String graphId) throws Throwable {  
        cvkLogger = new CVKGraphLogger(graphId);
        
        renderThread = Thread.currentThread();
        cvkLogger.info("Renderthread TID %d (java hash %d)", renderThread.getId(), renderThread.hashCode());
                
        cvkCanvas = new CVKCanvas(this);   
        
        int ret;
        ret = CVKAxesRenderable.StaticInitialise();
        if (VkFailed(ret)) { 
            cvkLogger.severe("Failed to statically initialise CVKAxesRenderable");
        }
        ret = CVKFPSRenderable.StaticInitialise();      
        if (VkFailed(ret)) { 
            cvkLogger.severe("Failed to statically initialise CVKFPSRenderable");
        }
        ret = CVKIconsRenderable.StaticInitialise();      
        if (VkFailed(ret)) { 
            cvkLogger.severe("Failed to statically initialise CVKIconsRenderable");
        }
        ret = CVKLabelsRenderable.StaticInitialise();      
        if (VkFailed(ret)) { 
            cvkLogger.severe("Failed to statically initialise CVKLabelsRenderable");
        }        
        ret = CVKPointsRenderable.StaticInitialise();
        if (VkFailed(ret)) { 
            cvkLogger.severe("Failed to statically initialise CVKPointsRenderable");
        }
        
        cvkIconTextureAtlas = new CVKIconTextureAtlas(this);     
        cvkCanvas.AddRenderable(cvkIconTextureAtlas);  
        cvkHitTester = new CVKHitTester(this);     
        cvkCanvas.AddRenderable(cvkHitTester);  
        cvkAxes = new CVKAxesRenderable(this);
        cvkCanvas.AddRenderable(cvkAxes);
        cvkFPS = new CVKFPSRenderable(this);    
        cvkCanvas.AddRenderable(cvkFPS);
        cvkIcons = new CVKIconsRenderable(this);
        cvkCanvas.AddRenderable(cvkIcons);
        cvkLabels = new CVKLabelsRenderable(this);
        cvkCanvas.AddRenderable(cvkLabels);
        cvkPoints = new CVKPointsRenderable(this);   
        cvkCanvas.AddRenderable(cvkPoints);        
    }
    
    
    public boolean IsRenderThreadCurrent() {
        return GetRenderThread() == Thread.currentThread();
    }
    
    public boolean IsRenderThreadAlive() {
        return renderThread != null ? renderThread.isAlive() : false;
    }
        
    public void VerifyInRenderThread() {
        if (CVK_DEBUGGING) {
            if (!IsRenderThreadCurrent()) {
                throw new RuntimeException(String.format("Error: render operation performed from thread %d, render thread %d",
                        Thread.currentThread().getId(), GetRenderThreadID()));
            }
        }
    }      
    
    
    void addTask(final CVKRenderUpdateTask task) {
        taskQueue.add(task);
    }
    
    @Override
    protected final void destroyCanvas() {     
        cvkCanvas.Destroy();
    }
    
    @Override
    public VisualOperation exportToImage(final File imageFile) {
        return new GLExportToImageOperation(imageFile);
    }

    @Override
    public VisualOperation exportToBufferedImage(final BufferedImage[] img1, final Semaphore waiter) {
        return new GLExportToBufferedImageOperation(img1, waiter);
    }

    /**
     * Retrieve the model view projection matrix currently being used for
     * visualisation. The projection matrix component is retrieved from the
     * {@link GLRenderer}.
     *
     * @return The MVP matrix this visual processor is using in its current
     * display cycle.
     */
    public final Matrix44f getDisplayModelViewProjectionMatrix() {
        Matrix44f mvpMatrix = new Matrix44f();
        mvpMatrix.multiply(projectionMatrix, modelViewMatrix);
        return mvpMatrix;
    }

    /**
     * Get the camera currently being used for visualisation.
     *
     * @return The Camera this visual processor is using in its current display
     * cycle.
     */
    public final Camera getDisplayCamera() {
        return camera;
    }

    /**
     * Sets the camera currently being used by this {@link VisualProcessor}.
     * <p>
     * This is only used by the {@link GraphRenderable} to ensure that the
     * Camera sent to the OpenGL context is in sync with the camera that can be
     * retrieved here using {@link #getDisplayCamera getDisplayCamera()}.
     *
     * @param camera
     */
    final void setDisplayCamera(final Camera camera) {
        this.camera = camera;
    }

    /**
     * Get the model view matrix currently being used for visualisation.
     *
     * @return The MV matrix this visual processor is using in its current
     * display cycle.
     */
    public final Matrix44f getDisplayModelViewMatrix() {
        return modelViewMatrix; // TODO: why is this different to Graphics3DUtilities.getModelViewMatrix(camera)?
    }

    /**
     * Get the model view projection matrix corresponding to the supplied
     * camera. The projection matrix component is retrieved from the
     * {@link GLRenderer}.
     *
     * @param camera The {@link Camera} from which to calculate the model view
     * component of the matrix.
     * @return The MVP matrix corresponding to the supplied camera and the
     * current projection of the {@link GLRenderer}.
     */
    protected Matrix44f getCameraModelViewProjectionMatrix(final Camera camera) {
        final Matrix44f mvMatrix = Graphics3DUtilities.getModelViewMatrix(camera);
        final Matrix44f mvpMatrix = new Matrix44f();
        mvpMatrix.multiply(projectionMatrix, mvMatrix);
        return mvpMatrix;
    }

    /**
     * Get the viewport (height, width, x, y) currently in use by the
     * {@link GLRenderer}.
     *
     * @return The viewport from the {@link GLRenderer}.
     */
    protected final int[] getViewport() {
        return cvkCanvas.GetRenderer().getViewport();
    }

    @Override
    public final void performVisualUpdate() {        
        // performVisualUpdate maybe called before the JPanel is added to its
        // parent.  We can't get a renderable surface until the parent chain is
        // intact.
        
        //if (shouldRender) {
            cvkCanvas.repaint();
            shouldRender = false;
        //}
    }

    @Override
    protected void initialise() {
    }

    @Override
    protected void cleanup() {
        // No need to destroy the canvas as there will be another call from the
        // visual manager to explicity destroy it.
        //cvkCanvas.Destroy();            
    }

    private final class GLExportToImageOperation implements VisualOperation {

        private final File file;

        public GLExportToImageOperation(final File file) {
            this.file = file;
        }

        @Override
        public void apply() {
           
            // Currently in the VisualProcessor thread, add a task to trigger
            // the save to file from the Render thread.
            if (cvkCanvas.GetRenderer() != null) {
                addTask(cvkCanvas.GetRenderer().TaskRequestScreenshot(file));
            }
//            graphRenderable.addTask(drawable -> {
//                final GL30 gl = drawable.getGL().getGL3();
//                gl.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
//                final AWTGLReadBufferUtil util = new AWTGLReadBufferUtil(drawable.getGLProfile(), false);
//                BufferedImage img = util.readPixelsToBufferedImage(gl, true);
//                // Write the image out as a PNG.
//                try {
//                    ImageIO.write(img, "png", file);
//                } catch (IOException ex) {
//                    GetLogger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
//                }
//            });
        }

        @Override
        public List<VisualChange> getVisualChanges() {
            return Arrays.asList(new VisualChangeBuilder(VisualProperty.EXTERNAL_CHANGE).build());
        }
    }

    private final class GLExportToBufferedImageOperation implements VisualOperation {

        private final BufferedImage[] img1;
        private final Semaphore waiter;

        /**
         * Export the current GL display to a BufferedImage.
         * <p>
         * The VisualProcessor paradigm doesn't lend itself to returning data,
         * due to the asynchronous operation queue. Therefore, a single element
         * array is passed in and a reference to the newly created BufferedImage
         * is assigned at index 0. The caller maintains a reference to this
         * array to access the BufferedImage. (An array is used to avoid
         * creating yet another class with only one property.)
         * <p>
         * Because the operation is asynchronous, the caller needs to know when
         * the BufferedImage is ready. A Semaphore with zero permits is passed
         * in. The operation releases a permit after the BufferedImage is
         * assigned. The caller waits on the Semaphore and proceeds when a
         * permit is acquired.
         *
         * @param img1 A single element array; the new BufferedImage is assigned
         * to index 0.
         * @param waiter A Semaphore with no permits available; a permit is
         * released when the BufferedImage has been assigned.
         */
        public GLExportToBufferedImageOperation(final BufferedImage[] img1, final Semaphore waiter) {
            this.img1 = img1;
            this.waiter = waiter;
        }

        @Override
        public void apply() {
            // TODO_TT: this whole func
//            graphRenderable.addTask(drawable -> {
//                final GL30 gl = drawable.getGL().getGL3();
//                gl.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
//                final AWTGLReadBufferUtil util = new AWTGLReadBufferUtil(drawable.getGLProfile(), false);
//                img1[0] = util.readPixelsToBufferedImage(gl, true);
//
//                waiter.release();
//            });
        }

        @Override
        public List<VisualChange> getVisualChanges() {
            return Arrays.asList(new VisualChangeBuilder(VisualProperty.EXTERNAL_CHANGE).build());
        }
    }

    /**
     * This exists so CVKRenderer can call it the wrapped function that would
     * otherwise have private exposure to it.
     */
    @Override
    protected void signalProcessorIdle() {
        super.signalProcessorIdle();
    }
    
    /**
     * This exists so CVKRenderer can call it the wrapped function that would
     * otherwise have private exposure to it.
     */    
    @Override
    protected void requestRedraw() {
        super.requestRedraw();
    }

    @Override
    protected void rebuild() {
        super.rebuild();
    }    

    @Override
    protected Component getCanvas() {
        return cvkCanvas;
    }
    
    public Rectangle getCanvasBounds() {
        return cvkCanvas.getBounds();
    }
    
    /**
     * All the double negatives
     * 
     * @return
     */
    public boolean surfaceReady() {
        return (cvkCanvas != null) ? !cvkCanvas.getBounds().isEmpty() : false;
    }
    
    public int ProcessRenderTasks(CVKSwapChain cvkSwapChain) {
        int ret = VK_SUCCESS;
        final List<CVKRenderUpdateTask> tasks = new ArrayList<>();
        taskQueue.drainTo(tasks);
        tasks.forEach(task -> { task.run(); });      
        return ret;
    }
    
    @Override
    public List<VisualChange> getFullRefreshSet(final VisualAccess access) {
        return Arrays.asList(
                new VisualChangeBuilder(VisualProperty.VERTICES_REBUILD).build(),
                new VisualChangeBuilder(VisualProperty.CONNECTIONS_REBUILD).build(),
                new VisualChangeBuilder(VisualProperty.BACKGROUND_COLOR).forItems(1).build(),
                new VisualChangeBuilder(VisualProperty.HIGHLIGHT_COLOUR).forItems(1).build(),
                new VisualChangeBuilder(VisualProperty.CONNECTIONS_OPACITY).forItems(1).build(),
                new VisualChangeBuilder(VisualProperty.BLAZE_SIZE).forItems(1).build(),
                //                new VisualChangeBuilder(VisualProperty.DRAW_FLAGS).forItems(1).build(),
                new VisualChangeBuilder(VisualProperty.CAMERA).forItems(1).build()
        );
    }

    @Override
    protected final Set<VisualProperty> getTrumpedProperties(final VisualProperty property) {
        switch (property) {
            case VERTICES_REBUILD:
                return new HashSet<>(Arrays.asList(
                        VisualProperty.VERTEX_SELECTED, VisualProperty.VERTEX_X,
                        VisualProperty.VERTEX_COLOR, VisualProperty.VERTEX_FOREGROUND_ICON,
                        VisualProperty.VERTEX_BLAZED, VisualProperty.BOTTOM_LABELS_REBUILD,
                        VisualProperty.TOP_LABELS_REBUILD, VisualProperty.DRAW_FLAGS
                ));
            case CONNECTIONS_REBUILD:
                return new HashSet<>(Arrays.asList(
                        VisualProperty.CONNECTION_SELECTED, VisualProperty.CONNECTION_COLOR,
                        VisualProperty.CONNECTION_LABELS_REBUILD
                ));
            case CONNECTION_LABELS_REBUILD:
                return new HashSet<>(Arrays.asList(
                        VisualProperty.CONNECTION_LABEL_COLOR
                ));
            case TOP_LABELS_REBUILD:
                return new HashSet<>(Arrays.asList(
                        VisualProperty.TOP_LABEL_COLOR
                ));
            case BOTTOM_LABELS_REBUILD:
                return new HashSet<>(Arrays.asList(
                        VisualProperty.BOTTOM_LABEL_COLOR
                ));
            default:
                return super.getTrumpedProperties(property);
        }
    }

    @Override
    protected final VisualProperty getMasterProperty(final VisualProperty property) {

        switch (property) {
            case BLAZE_SIZE:
            case BLAZE_OPACITY:
                return VisualProperty.BLAZE_SIZE;
            case VISIBLE_ABOVE_THRESHOLD:
            case VISIBILITY_THRESHOLD:
                return VisualProperty.DRAW_FLAGS;
            case VERTEX_SELECTED:
            case VERTEX_DIM:
                return VisualProperty.VERTEX_SELECTED;
            case VERTEX_RADIUS:
            case VERTEX_X:
            case VERTEX_Y:
            case VERTEX_Z:
            case VERTEX_X2:
            case VERTEX_Y2:
            case VERTEX_Z2:
                return VisualProperty.VERTEX_X;
            case VERTEX_COLOR:
                return VisualProperty.VERTEX_COLOR;
            case VERTEX_FOREGROUND_ICON:
            case VERTEX_BACKGROUND_ICON:
            case VERTEX_NW_DECORATOR:
            case VERTEX_NE_DECORATOR:
            case VERTEX_SW_DECORATOR:
            case VERTEX_SE_DECORATOR:
                return VisualProperty.VERTEX_FOREGROUND_ICON;
            case VERTEX_BLAZED:
            case VERTEX_BLAZE_ANGLE:
            case VERTEX_BLAZE_COLOR:
                return VisualProperty.VERTEX_BLAZED;
            case VERTEX_VISIBILITY:
            case VERTICES_ADDED:
            case VERTICES_REMOVED:
            case VERTICES_REBUILD:
                return VisualProperty.VERTICES_REBUILD;
            case CONNECTION_COLOR:
                return VisualProperty.CONNECTION_COLOR;
            case CONNECTION_SELECTED:
            case CONNECTION_DIRECTED:
            case CONNECTION_DIM:
            case CONNECTION_LINESTYLE:
                return VisualProperty.CONNECTION_SELECTED;
            case CONNECTION_VISIBILITY:
            case CONNECTION_WIDTH:
            case CONNECTIONS_ADDED:
            case CONNECTIONS_REMOVED:
            case CONNECTIONS_REBUILD:
                return VisualProperty.CONNECTIONS_REBUILD;
            case TOP_LABEL_SIZE:
            case TOP_LABELS_REBUILD:
            case TOP_LABEL_TEXT:
                return VisualProperty.TOP_LABELS_REBUILD;
            case BOTTOM_LABEL_SIZE:
            case BOTTOM_LABELS_REBUILD:
            case BOTTOM_LABEL_TEXT:
                return VisualProperty.BOTTOM_LABELS_REBUILD;
            case CONNECTION_LABEL_SIZE:
            case CONNECTION_LABELS_REBUILD:
            case CONNECTION_LABEL_TEXT:
                return VisualProperty.CONNECTION_LABELS_REBUILD;
            default:
                return super.getMasterProperty(property);
        }

    }

    @Override
    protected final VisualChangeProcessor getChangeProcessor(final VisualProperty property) {
        shouldRender = true;
        if (DEBUGGING_POINTS) {
            switch (property) {
                case VERTICES_REBUILD:
                    return (change, access) -> {                       
                        if (cvkPoints != null) {
                            addTask(cvkPoints.TaskRebuildPoints(access));
                        }
                    };
                case VERTEX_X:
                    return (change, access) -> {
                        if (cvkPoints != null) {
                            addTask(cvkPoints.TaskUpdatePoints(change, access));
                        }   
                    };
            case CAMERA:
                return (change, access) -> {
                    camera = access.getCamera();
                    setDisplayCamera(camera);
                    Graphics3DUtilities.getModelViewMatrix(camera.lookAtEye, camera.lookAtCentre, camera.lookAtUp, getDisplayModelViewMatrix());

                    if (cvkAxes != null) {
                        addTask(cvkAxes.TaskUpdateCamera());
                    }
                    if (cvkPoints != null) {
                        addTask(cvkPoints.TaskUpdateCamera());
                    }
                };      
            case EXTERNAL_CHANGE:
            default:
                shouldRender = false;
                return (change, access) -> {
                };                
            }
        }


        switch (property) {
            case VERTICES_REBUILD:
                return (change, access) -> {
                    addTask(cvkIcons.TaskUpdateIcons(change, access));
                    addTask(cvkIcons.TaskUpdatePositions(change, access));                         
                    addTask(cvkIcons.TaskUpdateVertexFlags(change, access));

                    addTask(cvkLabels.TaskUpdateLabels(change, access));
                    addTask(cvkLabels.TaskUpdateColours(change, access));
                    addTask(cvkLabels.TaskUpdateSizes(change, access));


//                    addTask(xyzTexturiser.dispose());
//                    addTask(xyzTexturiser.createTexture(access));
//                    addTask(vertexFlagsTexturiser.dispose());
//                    addTask(vertexFlagsTexturiser.createTexture(access));
//                    addTask(iconBatcher.disposeBatch());
//                    addTask(iconBatcher.createBatch(access));
//                    addTask(nodeLabelBatcher.disposeBatch());
//                    addTask(nodeLabelBatcher.createBatch(access));
//                    addTask(blazeBatcher.disposeBatch());
//                    addTask(blazeBatcher.createBatch(access));
//                    addTask(gl -> {
//                        iconTextureArray = iconBatcher.updateIconTexture(gl);
//                    });
//                    final DrawFlags updatedDrawFlags = access.getDrawFlags();
//                    addTask(gl -> {
//                        drawFlags = updatedDrawFlags;
//                    });
                };
            case CONNECTIONS_REBUILD:
                return (change, access) -> {
//                    addTask(connectionLabelBatcher.setLabelColors(access));
//                    addTask(connectionLabelBatcher.setLabelSizes(access));
//                    addTask(lineBatcher.disposeBatch());
//                    addTask(lineBatcher.createBatch(access));
//                    addTask(loopBatcher.disposeBatch());
//                    addTask(loopBatcher.createBatch(access));
//                    addTask(connectionLabelBatcher.disposeBatch());
//                    addTask(connectionLabelBatcher.createBatch(access));
                };
            case BACKGROUND_COLOR:
                return (change, access) -> {
                    final ConstellationColor backgroundColor = access.getBackgroundColor();
//                    addTask(gl -> {
//                        graphBackgroundColor = new float[]{backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), 1};
//                    });
//                    addTask(connectionLabelBatcher.setBackgroundColor(access));
                    addTask(cvkLabels.TaskSetBackgroundColor(access));
                };
            case HIGHLIGHT_COLOUR:
                return (change, access) -> {
                    addTask(cvkIcons.TaskSetHighlightColour(access));                   
                    addTask(cvkLabels.TaskSetHighlightColor(access));
//                    addTask(connectionLabelBatcher.setHighlightColor(access));
//                    addTask(lineBatcher.setHighlightColor(access));
//                    addTask(iconBatcher.setHighlightColor(access));
                };
            case DRAW_FLAGS:
                return (change, access) -> {
                    final DrawFlags updatedDrawFlags = access.getDrawFlags();
//                    addTask(gl -> {
//                        drawFlags = updatedDrawFlags;
//                    });
                };
            case BLAZE_SIZE:
                return (change, access) -> {
//                    addTask(blazeBatcher.updateSizeAndOpacity(access));
                };
            case CONNECTIONS_OPACITY:
                return (change, access) -> {
//                    addTask(lineBatcher.updateOpacity(access));
                };
            case BOTTOM_LABEL_COLOR:
                return (change, access) -> {
                    addTask(cvkLabels.TaskUpdateColours(change, access));
                };
            case BOTTOM_LABELS_REBUILD:
                return (change, access) -> {
                    addTask(cvkLabels.TaskUpdateLabels(change, access));
                    addTask(cvkLabels.TaskUpdateColours(change, access));
                    addTask(cvkLabels.TaskUpdateSizes(change, access));
                };
            case CAMERA:
                return (change, access) -> {
                    camera = access.getCamera();
                    setDisplayCamera(camera);
                    Graphics3DUtilities.getModelViewMatrix(camera.lookAtEye, camera.lookAtCentre, camera.lookAtUp, getDisplayModelViewMatrix());

                    addTask(cvkAxes.TaskUpdateCamera());
                    addTask(cvkIcons.TaskUpdateCamera());
                };
            case CONNECTION_LABEL_COLOR:
                return (change, access) -> {
//                    addTask(connectionLabelBatcher.setLabelColors(access));
                };
            case CONNECTION_LABELS_REBUILD:
                return (change, access) -> {
//                    addTask(connectionLabelBatcher.setLabelColors(access));
//                    addTask(connectionLabelBatcher.setLabelSizes(access));
//                    // Note that updating connection labels always rebuilds from scratch, so it is not an issue if the batch was not 'ready'.
//                    addTask(connectionLabelBatcher.updateLabels(access));
                };
            case TOP_LABEL_COLOR:
                return (change, access) -> {
                    addTask(cvkLabels.TaskUpdateColours(change, access));
                };
            case TOP_LABELS_REBUILD:
                return (change, access) -> {
                    addTask(cvkLabels.TaskUpdateLabels(change, access));
                    addTask(cvkLabels.TaskUpdateColours(change, access));
                    addTask(cvkLabels.TaskUpdateSizes(change, access));                    
                };
            case CONNECTION_COLOR:
                return (change, access) -> {
//                    addTaskIfReady(loopBatcher.updateColors(access, change), loopBatcher);
//                    addTaskIfReady(lineBatcher.updateColors(access, change), lineBatcher);
                };
            case CONNECTION_SELECTED:
                return (change, access) -> {
//                    addTaskIfReady(loopBatcher.updateInfo(access, change), loopBatcher);
//                    addTaskIfReady(lineBatcher.updateInfo(access, change), lineBatcher);
                };
            case VERTEX_BLAZED:
                return (change, access) -> {
                    // Note that updating blazes always rebuilds from scratch, so it is not an issue if the batch was not 'ready'.
//                    addTask(blazeBatcher.updateBlazes(access, change));
                };
            case VERTEX_COLOR:
                return (change, access) -> {
                    if (cvkIcons != null) {
                        addTask(cvkIcons.TaskUpdateColours(change, access));
                    }                    
                };
            case VERTEX_FOREGROUND_ICON:
                return (change, access) -> {
                    // This is a task for the Icon Atlas
		    // TODO: implement this once we have a working hit tester
//                    if (cvkIcons != null) {
//                        addTask(cvkIcons.TaskUpdateIcons(change, access));
//                    }
                };

            case VERTEX_SELECTED:
                return (change, access) -> {
                    addTask(cvkIcons.TaskUpdateVertexFlags(change, access));                      
                };
            case VERTEX_X:
                return (change, access) -> {
                    try {
                        if (cvkIcons != null) {
//                            addTask(cvkIcons.TaskUpdateIcons(change, access));
                        }
                    } catch (Exception e) {
                        cvkLogger.LogException(e, "Exception thrown processing visual change %s:", property);
                        throw e;
                    }                            
                };
            case EXTERNAL_CHANGE:
            default:
                shouldRender = false;
                return (change, access) -> {
                };
        }     
    }
        
    public void SwapChainRecreated(CVKDevice cvkDevice, CVKSwapChain cvkSwapChain) {     
        // Create the projection matrix, and load it on the projection matrix stack.
        viewFrustum.setPerspective(FIELD_OF_VIEW, (float)cvkSwapChain.GetWidth() / (float)cvkSwapChain.GetHeight(), PERSPECTIVE_NEAR, PERSPECTIVE_FAR);           
        pixelDensity = (float)(cvkSwapChain.GetHeight() * 0.5 / Math.tan(Math.toRadians(FIELD_OF_VIEW)));        
        projectionMatrix.set(viewFrustum.getProjectionMatrix());
        
        // Compared to OpenGL Vulkan has a RHS with Y pointing down and it also 
        // calculates the Z differently in clipspace.  The Y needs to be flipped
        // screenspace in whatever shader applies the projection matrix.  The 
        // clipspace Z calculation can either be applied in the shader at the same
        // time by averaging gl_position z and w after the projection or it can
        // be applied to the projection matrix.  The latter is cheaper as it we
        // do it once for the per scene projection matrix rather than having to
        // do it per vertex for every renderable.
        //
        // NOTE: this appears not to be needed.  Leaving this note here for now
        // until there is time to analyse the required linear algebra with
        // RenderDoc.
        //
        // Reference: https://matthewwellings.com/blog/the-new-vulkan-coordinate-system/
//        Matrix44f pre = new Matrix44f();
//        pre.makeIdentity();
//        pre.set(1, 1, -1);
//        pre.set(2, 2, 0.5f);
//        pre.set(2, 3, 0.5f);        
//        projectionMatrix.multiply(pre, viewFrustum.getProjectionMatrix());
    }    

    public List<CVKRenderable> GetHitTesterList() {
        // TODO Hydra - Cache me
        List<CVKRenderable> hitTesters = new ArrayList<>();
        hitTesters.add(cvkIcons);
        return hitTesters;
    }
}
