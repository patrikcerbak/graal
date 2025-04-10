/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.hotspot;

import jdk.graal.compiler.serviceprovider.ServiceProvider;

import jdk.vm.ci.hotspot.HotSpotVMEventListener;
import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.services.JVMCIServiceLocator;

@ServiceProvider(JVMCIServiceLocator.class)
public final class HotSpotGraalJVMCIServiceLocator extends JVMCIServiceLocator {

    /**
     * Holds the state shared between all {@link HotSpotGraalJVMCIServiceLocator} instances. This is
     * necessary as a service provider instance is created each time the service is loaded.
     */
    private static final class Shared {
        static final Shared SINGLETON = new Shared();

        <T> T getProvider(Class<T> service, HotSpotGraalJVMCIServiceLocator locator) {
            if (service == JVMCICompilerFactory.class) {
                return service.cast(new HotSpotGraalCompilerFactory(locator));
            } else if (service == HotSpotVMEventListener.class) {
                if (graalRuntime != null) {
                    return service.cast(new HotSpotGraalVMEventListener(graalRuntime));
                }
                // Need to hold onto the listener to connect it with a Graal runtime
                // once we have one. This supports a JVMCI runtime that eagerly
                // creates its VM event listeners (JDK-8315566).
                if (vmEventListener == null) {
                    vmEventListener = new HotSpotGraalVMEventListener(null);
                }
                return service.cast(vmEventListener);
            }
            return null;
        }

        private HotSpotGraalRuntime graalRuntime;
        private HotSpotGraalVMEventListener vmEventListener;

        /**
         * Notifies this object of the compiler created via {@link HotSpotGraalJVMCIServiceLocator}.
         */
        void onCompilerCreation(HotSpotGraalCompiler compiler) {
            assert this.graalRuntime == null : "only expect a single JVMCICompiler to be created";
            this.graalRuntime = (HotSpotGraalRuntime) compiler.getGraalRuntime();
            if (this.vmEventListener != null) {
                this.vmEventListener.setRuntime(graalRuntime);
            }
        }
    }

    @Override
    public <T> T getProvider(Class<T> service) {
        return Shared.SINGLETON.getProvider(service, this);
    }

    /**
     * Notifies this object of the compiler created via {@link HotSpotGraalJVMCIServiceLocator}.
     */
    @SuppressWarnings("static-method")
    void onCompilerCreation(HotSpotGraalCompiler compiler) {
        Shared.SINGLETON.onCompilerCreation(compiler);
    }
}
