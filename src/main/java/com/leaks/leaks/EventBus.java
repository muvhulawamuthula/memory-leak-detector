package com.leaks.leaks;

import java.util.ArrayList;
import java.util.List;

public class EventBus {

    private static final EventBus INSTANCE = new EventBus();

    private final List<EventListener> listeners = new ArrayList<>();

    private EventBus() {}

    public static EventBus getInstance() {
        return INSTANCE;
    }

    public void register(EventListener listener) {
        listeners.add(listener);
    }

    public void unregister(EventListener listener) {
        listeners.remove(listener);
    }

    public void publish(String event) {
        for (EventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    public int listenerCount() {
        return listeners.size();
    }

    public interface EventListener {
        void onEvent(String event);
    }
}