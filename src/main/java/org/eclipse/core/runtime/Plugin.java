package org.eclipse.core.runtime;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/** Probe placeholder: EMF's EclipsePlugin extends this, but only runs inside Eclipse. */
public abstract class Plugin implements BundleActivator {
    @Override public void start(BundleContext context) throws Exception { }
    @Override public void stop(BundleContext context) throws Exception { }
}
