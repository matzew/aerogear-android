/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.aerogear.android.unifiedpush;

import android.content.Context;
import android.os.Bundle;


public interface MessageHandler {

    public void onDeleteMessage(Context context, Bundle message);

    public void onMessage(Context context, Bundle message);

    public void onError();
}
