/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.ui;

import android.content.Context;
import android.graphics.Matrix;
import com.android.gallery3d.anim.CanvasAnimation;

public class GLRootMock implements GLRoot {
    int mRequestRenderCalled;
    int mRequestLayoutContentPaneCalled;

    public void addOnGLIdleListener(OnGLIdleListener listener) {}
    public void registerLaunchedAnimation(CanvasAnimation animation) {}
    public void requestRenderForced() {
        mRequestRenderCalled++;
    }
    public void requestRender() {
        mRequestRenderCalled++;
    }
    public void requestLayoutContentPane() {
        mRequestLayoutContentPaneCalled++;
    }
    public boolean hasStencil() { return true; }
    public void lockRenderThread() {}
    public void unlockRenderThread() {}
    public void setContentPane(GLView content) {}
    public void setOrientationSource(OrientationSource source) {}
    public int getDisplayRotation() { return 0; }
    public int getCompensation() { return 0; }
    public Matrix getCompensationMatrix() { return null; }
    public void freeze() {}
    public void unfreeze() {}
    public void setLightsOutMode(boolean enabled) {}
    public Context getContext() { return null; }
}
