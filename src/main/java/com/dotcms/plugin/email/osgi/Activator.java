package com.dotcms.plugin.email.osgi;

import com.dotcms.repackage.org.osgi.framework.BundleContext;

import com.dotcms.plugin.email.actionlet.EmailActionlet;
import com.dotmarketing.osgi.GenericBundleActivator;


public class Activator extends GenericBundleActivator {





    @SuppressWarnings ("unchecked")
    public void start ( BundleContext context ) throws Exception {

        //Initializing services...
        initializeServices( context );



        //Registering the test Actionlet
        registerActionlet( context, new EmailActionlet() );
    }


    public void stop ( BundleContext context ) throws Exception {


        
        unregisterActionlets();

    }

}