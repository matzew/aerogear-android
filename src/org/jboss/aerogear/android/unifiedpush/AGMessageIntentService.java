/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.aerogear.android.unifiedpush;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

/**
 *
 * @author summers
 */
public class AGMessageIntentService extends IntentService {

    private AGMessageResultReceiver receiver;

    public AGMessageIntentService() {
        super("");
    }
    
    @Override
    public void onStart(Intent intent, int startId) {
        receiver = intent.getExtras().getParcelable("Receiver");
    }

    
    
    @Override
    protected void onHandleIntent(Intent intent) {
        receiver.send(-1, intent.getExtras());
    }
    
}
