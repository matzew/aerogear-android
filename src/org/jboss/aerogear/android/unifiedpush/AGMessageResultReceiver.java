/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.aerogear.android.unifiedpush;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

/**
 *
 * @author summers
 */
public class AGMessageResultReceiver extends ResultReceiver {
    
    public AGMessageResultReceiver(Handler handler) {
        super(handler);
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        
    }

    public static interface Receiver {
        
        public void onDeleteMessage();
        public void onMessage();
        public void onError();
        
    }
    
}
