
package com.chat.common;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
//bus that connects UI to the main part of the app
public class UIEventBus {
    private static final CopyOnWriteArrayList<Consumer<Object>> listeners = new CopyOnWriteArrayList<>();

    public static void subscribe(Consumer<Object> l) { listeners.add(l); }
    public static void publish(Object evt) {
        for (Consumer<Object> l : listeners) l.accept(evt);
    }
}
