/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.asyncfx.beans.binding;

import com.sun.javafx.binding.ExpressionHelperBase;
import java.util.Arrays;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.asyncfx.PublishSource;
import org.asyncfx.beans.AsyncSubObservable;
import org.asyncfx.beans.SubInvalidationListener;
import org.asyncfx.beans.value.AsyncObservableValue;
import org.asyncfx.beans.value.AsyncSubObservableValue;
import org.asyncfx.beans.value.SubChangeListener;
import org.jetbrains.annotations.Nullable;

@PublishSource(
    module = "openjfx",
    licenses = {"openjfx", "intel-gpl-classpath-exception"}
)
public abstract class ProxyAsyncExpressionHelper<T> extends ExpressionHelperBase {

    public static <T> ProxyAsyncExpressionHelper<T> addListener(
            ProxyAsyncExpressionHelper<T> helper,
            ObservableValue<T> observable,
            @Nullable AsyncObservableValue<T> peer,
            T currentValue,
            InvalidationListener listener) {
        if ((observable == null) || (listener == null)) {
            throw new NullPointerException();
        }

        return (helper == null)
            ? new SingleInvalidation<>(observable, peer, listener)
            : helper.addListener(listener, currentValue);
    }

    public static <T> ProxyAsyncExpressionHelper<T> removeListener(
            ProxyAsyncExpressionHelper<T> helper, T currentValue, InvalidationListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }

        return (helper == null) ? null : helper.removeListener(listener, currentValue);
    }

    public static <T> ProxyAsyncExpressionHelper<T> addListener(
            ProxyAsyncExpressionHelper<T> helper,
            ObservableValue<T> observable,
            @Nullable AsyncObservableValue<T> peer,
            T currentValue,
            SubInvalidationListener listener) {
        if ((observable == null) || (listener == null)) {
            throw new NullPointerException();
        }

        return (helper == null)
            ? new SingleSubInvalidation<>(observable, peer, listener)
            : helper.addListener(listener, currentValue);
    }

    public static <T> ProxyAsyncExpressionHelper<T> removeListener(
            ProxyAsyncExpressionHelper<T> helper, T currentValue, SubInvalidationListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }

        return (helper == null) ? null : helper.removeListener(listener, currentValue);
    }

    public static <T> ProxyAsyncExpressionHelper<T> addListener(
            ProxyAsyncExpressionHelper<T> helper,
            ObservableValue<T> observable,
            @Nullable AsyncObservableValue<T> peer,
            T currentValue,
            ChangeListener<? super T> listener) {
        if ((observable == null) || (listener == null)) {
            throw new NullPointerException();
        }

        return (helper == null)
            ? new SingleChange<>(observable, peer, currentValue, listener)
            : helper.addListener(listener, currentValue);
    }

    public static <T> ProxyAsyncExpressionHelper<T> removeListener(
            ProxyAsyncExpressionHelper<T> helper, T currentValue, ChangeListener<? super T> listener) {
        if (listener == null) {
            throw new NullPointerException();
        }

        return (helper == null) ? null : helper.removeListener(listener, currentValue);
    }

    public static <T> ProxyAsyncExpressionHelper<T> addListener(
            ProxyAsyncExpressionHelper<T> helper,
            ObservableValue<T> observable,
            @Nullable AsyncObservableValue<T> peer,
            T currentValue,
            SubChangeListener listener) {
        if ((observable == null) || (listener == null)) {
            throw new NullPointerException();
        }

        return (helper == null)
            ? new SingleSubChange<>(observable, peer, currentValue, listener)
            : helper.addListener(listener, currentValue);
    }

    public static <T> ProxyAsyncExpressionHelper<T> removeListener(
            ProxyAsyncExpressionHelper<T> helper, T currentValue, SubChangeListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }

        return (helper == null) ? null : helper.removeListener(listener, currentValue);
    }

    public static <T> boolean validatesValue(ProxyAsyncExpressionHelper<T> helper) {
        if (helper != null) {
            return helper.validatesValue();
        }

        return false;
    }

    public static <T> boolean containsBidirectionalBindingEndpoints(ProxyAsyncExpressionHelper<T> helper) {
        if (helper != null) {
            return helper.containsBidirectionalBindingEndpoints();
        }

        return false;
    }

    public static <T> void fireValueChangedEvent(
            ProxyAsyncExpressionHelper<T> helper, T currentValue, boolean subChange) {
        if (helper != null) {
            helper.fireValueChangedEvent(currentValue, subChange);
        }
    }

    public static <T> void setPeer(ProxyAsyncExpressionHelper<T> helper, @Nullable AsyncObservableValue<T> peer) {
        if (helper != null) {
            helper.setPeer(peer);
        }
    }

    protected final ObservableValue<T> observable;
    protected AsyncObservableValue<T> peer;

    private ProxyAsyncExpressionHelper(ObservableValue<T> observable, @Nullable AsyncObservableValue<T> peer) {
        this.observable = observable;
        this.peer = peer;
    }

    protected abstract ProxyAsyncExpressionHelper<T> addListener(InvalidationListener listener, T currentValue);

    protected abstract ProxyAsyncExpressionHelper<T> removeListener(InvalidationListener listener, T currentValue);

    protected abstract ProxyAsyncExpressionHelper<T> addListener(SubInvalidationListener listener, T currentValue);

    protected abstract ProxyAsyncExpressionHelper<T> removeListener(SubInvalidationListener listener, T currentValue);

    protected abstract ProxyAsyncExpressionHelper<T> addListener(ChangeListener<? super T> listener, T currentValue);

    protected abstract ProxyAsyncExpressionHelper<T> removeListener(ChangeListener<? super T> listener, T currentValue);

    protected abstract ProxyAsyncExpressionHelper<T> addListener(SubChangeListener listener, T currentValue);

    protected abstract ProxyAsyncExpressionHelper<T> removeListener(SubChangeListener listener, T currentValue);

    protected abstract boolean validatesValue();

    protected abstract boolean containsBidirectionalBindingEndpoints();

    protected abstract void fireValueChangedEvent(T newValue, boolean subChange);

    protected abstract void setPeer(@Nullable AsyncObservableValue<T> peer);

    static class SingleInvalidation<T> extends ProxyAsyncExpressionHelper<T> {

        private final InvalidationListener peerListener =
            new InvalidationListener() {
                @Override
                public void invalidated(Observable observable) {
                    listener.invalidated(SingleInvalidation.this.observable);
                }
            };

        private final InvalidationListener listener;

        private SingleInvalidation(
                ObservableValue<T> expression, @Nullable AsyncObservableValue<T> peer, InvalidationListener listener) {
            super(expression, peer);
            this.listener = listener;
            addPeerListener();
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(InvalidationListener listener, T currentValue) {
            removePeerListener();
            return new Generic<>(observable, peer, currentValue, this.listener, listener);
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(InvalidationListener listener, T currentValue) {
            if (this.listener != null && this.listener.equals(listener)) {
                removePeerListener();
                return null;
            }

            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(SubInvalidationListener listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(SubInvalidationListener listener, T currentValue) {
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(ChangeListener<? super T> listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(ChangeListener<? super T> listener, T currentValue) {
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(SubChangeListener listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(SubChangeListener listener, T currentValue) {
            return this;
        }

        @Override
        protected boolean validatesValue() {
            return false;
        }

        @Override
        protected boolean containsBidirectionalBindingEndpoints() {
            return false;
        }

        @Override
        protected void fireValueChangedEvent(T newValue, boolean subChange) {
            if (subChange) {
                return;
            }

            try {
                listener.invalidated(observable);
            } catch (Exception e) {
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        }

        @Override
        protected void setPeer(@Nullable AsyncObservableValue<T> peer) {
            removePeerListener();
            this.peer = peer;
            addPeerListener();
            fireValueChangedEvent(observable.getValue(), false);
        }

        private void addPeerListener() {
            if (peer != null) {
                peer.addListener(peerListener);
            }
        }

        private void removePeerListener() {
            if (peer != null) {
                peer.removeListener(peerListener);
            }
        }
    }

    static class SingleSubInvalidation<T> extends ProxyAsyncExpressionHelper<T> {

        private final SubInvalidationListener peerListener =
            new SubInvalidationListener() {
                @Override
                public void invalidated(Observable observable, boolean subInvalidation) {
                    listener.invalidated(SingleSubInvalidation.this.observable, subInvalidation);
                }
            };

        private final SubInvalidationListener listener;

        private SingleSubInvalidation(
                ObservableValue<T> expression,
                @Nullable AsyncObservableValue<T> peer,
                SubInvalidationListener listener) {
            super(expression, peer);
            this.listener = listener;
            addPeerListener();
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(InvalidationListener listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(InvalidationListener listener, T currentValue) {
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(SubInvalidationListener listener, T currentValue) {
            removePeerListener();
            return new Generic<>(observable, peer, currentValue, this.listener, listener);
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(SubInvalidationListener listener, T currentValue) {
            if (this.listener != null && this.listener.equals(listener)) {
                removePeerListener();
                return null;
            }

            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(ChangeListener<? super T> listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(ChangeListener<? super T> listener, T currentValue) {
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(SubChangeListener listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(SubChangeListener listener, T currentValue) {
            return this;
        }

        @Override
        protected boolean validatesValue() {
            return false;
        }

        @Override
        protected boolean containsBidirectionalBindingEndpoints() {
            return false;
        }

        @Override
        protected void fireValueChangedEvent(T newValue, boolean subChange) {
            try {
                listener.invalidated(observable, subChange);
            } catch (Exception e) {
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        }

        @Override
        protected void setPeer(@Nullable AsyncObservableValue<T> peer) {
            removePeerListener();
            this.peer = peer;
            addPeerListener();
            fireValueChangedEvent(observable.getValue(), false);
        }

        private void addPeerListener() {
            if (peer instanceof AsyncSubObservable) {
                ((AsyncSubObservable)peer).addListener(peerListener);
            }
        }

        private void removePeerListener() {
            if (peer instanceof AsyncSubObservable) {
                ((AsyncSubObservable)peer).removeListener(peerListener);
            }
        }
    }

    static class SingleChange<T> extends ProxyAsyncExpressionHelper<T> {

        private final ChangeListener<? super T> peerListener =
            new ChangeListener<>() {
                @Override
                public void changed(ObservableValue<? extends T> observable, T oldValue, T newValue) {
                    listener.changed(SingleChange.this.observable, oldValue, newValue);
                }
            };

        private final ChangeListener<? super T> listener;
        private T currentValue;

        private SingleChange(
                ObservableValue<T> observable,
                AsyncObservableValue<T> peer,
                T currentValue,
                ChangeListener<? super T> listener) {
            super(observable, peer);
            this.listener = listener;
            this.currentValue = currentValue;
            addPeerListener();
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(InvalidationListener listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(InvalidationListener listener, T currentValue) {
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(SubInvalidationListener listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(SubInvalidationListener listener, T currentValue) {
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(ChangeListener<? super T> listener, T currentValue) {
            removePeerListener();
            return new Generic<>(observable, peer, currentValue, this.listener, listener);
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(ChangeListener<? super T> listener, T currentValue) {
            if (this.listener != null && this.listener.equals(listener)) {
                removePeerListener();
                return null;
            }

            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(SubChangeListener listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(SubChangeListener listener, T currentValue) {
            return this;
        }

        @Override
        protected boolean validatesValue() {
            return true;
        }

        @Override
        protected boolean containsBidirectionalBindingEndpoints() {
            return listener instanceof BidirectionalBindingMarker;
        }

        @Override
        protected void fireValueChangedEvent(T newValue, boolean subChange) {
            if (subChange) {
                return;
            }

            final T oldValue = currentValue;
            currentValue = newValue;
            final boolean changed = !Objects.equals(currentValue, oldValue);
            if (changed) {
                try {
                    listener.changed(observable, oldValue, currentValue);
                } catch (Exception e) {
                    Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
                }
            }
        }

        @Override
        protected void setPeer(@Nullable AsyncObservableValue<T> peer) {
            removePeerListener();
            this.peer = peer;
            addPeerListener();
            fireValueChangedEvent(observable.getValue(), false);
        }

        private void addPeerListener() {
            if (peer != null) {
                peer.addListener(peerListener);
            }
        }

        private void removePeerListener() {
            if (peer != null) {
                peer.removeListener(peerListener);
            }
        }
    }

    static class SingleSubChange<T> extends ProxyAsyncExpressionHelper<T> {

        private final SubChangeListener peerListener =
            new SubChangeListener() {
                @Override
                public void changed(
                        ObservableValue<?> observable, Object oldValue, Object newValue, boolean subChange) {
                    listener.changed(observable, oldValue, newValue, subChange);
                }
            };

        private final SubChangeListener listener;
        private T currentValue;

        private SingleSubChange(
                ObservableValue<T> observable,
                @Nullable AsyncObservableValue<T> peer,
                T currentValue,
                SubChangeListener listener) {
            super(observable, peer);
            this.listener = listener;
            this.currentValue = currentValue;
            addPeerListener();
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(InvalidationListener listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(InvalidationListener listener, T currentValue) {
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(SubInvalidationListener listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(SubInvalidationListener listener, T currentValue) {
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(ChangeListener<? super T> listener, T currentValue) {
            removePeerListener();
            ProxyAsyncExpressionHelper<T> helper = new Generic<>(observable, peer, currentValue);
            helper.addListener(this.listener, currentValue);
            helper.addListener(listener, currentValue);
            return helper;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(ChangeListener<? super T> listener, T currentValue) {
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(SubChangeListener listener, T currentValue) {
            removePeerListener();
            return new Generic<>(observable, peer, currentValue, this.listener, listener);
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(SubChangeListener listener, T currentValue) {
            if (this.listener != null && this.listener.equals(listener)) {
                removePeerListener();
                return null;
            }

            return this;
        }

        @Override
        protected boolean validatesValue() {
            return true;
        }

        @Override
        protected boolean containsBidirectionalBindingEndpoints() {
            return false;
        }

        @Override
        protected void fireValueChangedEvent(T newValue, boolean subChange) {
            final T oldValue = currentValue;
            currentValue = newValue;
            try {
                final boolean changed = subChange || (!Objects.equals(currentValue, oldValue));
                if (changed) {
                    listener.changed(observable, oldValue, currentValue, subChange);
                }
            } catch (Exception e) {
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        }

        @Override
        protected void setPeer(@Nullable AsyncObservableValue<T> peer) {
            removePeerListener();
            this.peer = peer;
            addPeerListener();
            fireValueChangedEvent(observable.getValue(), false);
        }

        @SuppressWarnings("unchecked")
        private void addPeerListener() {
            if (peer instanceof AsyncSubObservableValue) {
                ((AsyncSubObservableValue<T>)peer).addListener(peerListener);
            }
        }

        @SuppressWarnings("unchecked")
        private void removePeerListener() {
            if (peer instanceof AsyncSubObservableValue) {
                ((AsyncSubObservableValue<T>)peer).removeListener(peerListener);
            }
        }
    }

    static class Generic<T> extends ProxyAsyncExpressionHelper<T> {

        private InvalidationListener peerInvalidationListener;
        private SubInvalidationListener peerSubInvalidationListener;
        private ChangeListener<? super T> peerChangeListener;
        private SubChangeListener peerSubChangeListener;
        private InvalidationListener[] invalidationListeners;
        private SubInvalidationListener[] subInvalidationListeners;
        private ChangeListener<? super T>[] changeListeners;
        private SubChangeListener[] subChangeListeners;
        private int invalidationSize;
        private int subInvalidationSize;
        private int changeSize;
        private int subChangeSize;
        protected boolean locked;
        protected T currentValue;

        private Generic(ObservableValue<T> observable, AsyncObservableValue<T> peer, T currentValue) {
            super(observable, peer);
            this.currentValue = currentValue;
        }

        private Generic(
                ObservableValue<T> observable,
                AsyncObservableValue<T> peer,
                T currentValue,
                InvalidationListener listener0,
                InvalidationListener listener1) {
            super(observable, peer);
            this.invalidationListeners = new InvalidationListener[] {listener0, listener1};
            this.invalidationSize = 2;
            this.currentValue = currentValue;
            ensurePeerInvalidationListener();
        }

        private Generic(
                ObservableValue<T> observable,
                AsyncObservableValue<T> peer,
                T currentValue,
                SubInvalidationListener listener0,
                SubInvalidationListener listener1) {
            super(observable, peer);
            this.subInvalidationListeners = new SubInvalidationListener[] {listener0, listener1};
            this.subInvalidationSize = 2;
            this.currentValue = currentValue;
            ensurePeerSubInvalidationListener();
        }

        @SuppressWarnings("unchecked")
        private Generic(
                ObservableValue<T> observable,
                AsyncObservableValue<T> peer,
                T currentValue,
                ChangeListener<? super T> listener0,
                ChangeListener<? super T> listener1) {
            super(observable, peer);
            this.changeListeners = new ChangeListener[] {listener0, listener1};
            this.changeSize = 2;
            this.currentValue = currentValue;
            ensurePeerChangeListener();
        }

        private Generic(
                ObservableValue<T> observable,
                AsyncObservableValue<T> peer,
                T currentValue,
                SubChangeListener listener0,
                SubChangeListener listener1) {
            super(observable, peer);
            this.subChangeListeners = new SubChangeListener[] {listener0, listener1};
            this.subChangeSize = 2;
            this.currentValue = currentValue;
            ensurePeerSubChangeListener();
        }

        @Override
        protected Generic<T> addListener(InvalidationListener listener, T currentValue) {
            if (invalidationListeners == null) {
                invalidationListeners = new InvalidationListener[] {listener};
                invalidationSize = 1;
            } else {
                final int oldCapacity = invalidationListeners.length;
                if (locked) {
                    final int newCapacity = (invalidationSize < oldCapacity) ? oldCapacity : (oldCapacity * 3) / 2 + 1;
                    invalidationListeners = Arrays.copyOf(invalidationListeners, newCapacity);
                } else if (invalidationSize == oldCapacity) {
                    invalidationSize = trim(invalidationSize, invalidationListeners);
                    if (invalidationSize == oldCapacity) {
                        final int newCapacity = (oldCapacity * 3) / 2 + 1;
                        invalidationListeners = Arrays.copyOf(invalidationListeners, newCapacity);
                    }
                }

                invalidationListeners[invalidationSize++] = listener;
            }

            ensurePeerInvalidationListener();
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(InvalidationListener listener, T currentValue) {
            if (invalidationListeners != null) {
                for (int index = 0; index < invalidationSize; index++) {
                    if (invalidationListeners[index].equals(listener)) {
                        ProxyAsyncExpressionHelper<T> helper = getSingleListenerHelper(index, 1, 0, 0, 0);
                        if (helper != null) {
                            return helper;
                        }

                        if (invalidationSize == 1) {
                            invalidationListeners = null;
                            invalidationSize = 0;
                        } else {
                            final int numMoved = invalidationSize - index - 1;
                            final InvalidationListener[] oldListeners = invalidationListeners;
                            if (locked) {
                                invalidationListeners = new InvalidationListener[invalidationListeners.length];
                                System.arraycopy(oldListeners, 0, invalidationListeners, 0, index);
                            }

                            if (numMoved > 0) {
                                System.arraycopy(oldListeners, index + 1, invalidationListeners, index, numMoved);
                            }

                            invalidationSize--;
                            if (!locked) {
                                invalidationListeners[invalidationSize] = null; // Let gc do its work
                            }
                        }

                        break;
                    }
                }
            }

            return this;
        }

        @Override
        protected Generic<T> addListener(SubInvalidationListener listener, T currentValue) {
            if (subInvalidationListeners == null) {
                subInvalidationListeners = new SubInvalidationListener[] {listener};
                subInvalidationSize = 1;
            } else {
                final int oldCapacity = subInvalidationListeners.length;
                if (locked) {
                    final int newCapacity =
                        (subInvalidationSize < oldCapacity) ? oldCapacity : (oldCapacity * 3) / 2 + 1;
                    subInvalidationListeners = Arrays.copyOf(subInvalidationListeners, newCapacity);
                } else if (subInvalidationSize == oldCapacity) {
                    subInvalidationSize = trim(subInvalidationSize, subInvalidationListeners);
                    if (subInvalidationSize == oldCapacity) {
                        final int newCapacity = (oldCapacity * 3) / 2 + 1;
                        subInvalidationListeners = Arrays.copyOf(subInvalidationListeners, newCapacity);
                    }
                }

                subInvalidationListeners[subInvalidationSize++] = listener;
            }

            ensurePeerSubInvalidationListener();
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(SubInvalidationListener listener, T currentValue) {
            if (subInvalidationListeners != null) {
                for (int index = 0; index < subInvalidationSize; index++) {
                    if (subInvalidationListeners[index].equals(listener)) {
                        ProxyAsyncExpressionHelper<T> helper = getSingleListenerHelper(index, 0, 1, 0, 0);
                        if (helper != null) {
                            return helper;
                        }

                        if (subInvalidationSize == 1) {
                            subInvalidationListeners = null;
                            subInvalidationSize = 0;
                        } else {
                            final int numMoved = subInvalidationSize - index - 1;
                            final SubInvalidationListener[] oldListeners = subInvalidationListeners;
                            if (locked) {
                                subInvalidationListeners = new SubInvalidationListener[subInvalidationListeners.length];
                                System.arraycopy(oldListeners, 0, subInvalidationListeners, 0, index);
                            }

                            if (numMoved > 0) {
                                System.arraycopy(oldListeners, index + 1, subInvalidationListeners, index, numMoved);
                            }

                            subInvalidationSize--;
                            if (!locked) {
                                subInvalidationListeners[subInvalidationSize] = null; // Let gc do its work
                            }
                        }

                        break;
                    }
                }
            }

            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected ProxyAsyncExpressionHelper<T> addListener(ChangeListener<? super T> listener, T currentValue) {
            if (changeListeners == null) {
                changeListeners = new ChangeListener[] {listener};
                changeSize = 1;
            } else {
                final int oldCapacity = changeListeners.length;
                if (locked) {
                    final int newCapacity = (changeSize < oldCapacity) ? oldCapacity : (oldCapacity * 3) / 2 + 1;
                    changeListeners = Arrays.copyOf(changeListeners, newCapacity);
                } else if (changeSize == oldCapacity) {
                    changeSize = trim(changeSize, changeListeners);
                    if (changeSize == oldCapacity) {
                        final int newCapacity = (oldCapacity * 3) / 2 + 1;
                        changeListeners = Arrays.copyOf(changeListeners, newCapacity);
                    }
                }

                changeListeners[changeSize++] = listener;
            }

            if (changeSize == 1) {
                this.currentValue = currentValue;
            }

            ensurePeerChangeListener();
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected ProxyAsyncExpressionHelper<T> removeListener(ChangeListener<? super T> listener, T currentValue) {
            if (changeListeners != null) {
                for (int index = 0; index < changeSize; index++) {
                    if (changeListeners[index].equals(listener)) {
                        ProxyAsyncExpressionHelper<T> helper = getSingleListenerHelper(index, 0, 0, 1, 0);
                        if (helper != null) {
                            return helper;
                        }

                        if (changeSize == 1) {
                            changeListeners = null;
                            changeSize = 0;
                        } else {
                            final int numMoved = changeSize - index - 1;
                            final ChangeListener<? super T>[] oldListeners = changeListeners;
                            if (locked) {
                                changeListeners = new ChangeListener[changeListeners.length];
                                System.arraycopy(oldListeners, 0, changeListeners, 0, index);
                            }

                            if (numMoved > 0) {
                                System.arraycopy(oldListeners, index + 1, changeListeners, index, numMoved);
                            }

                            changeSize--;
                            if (!locked) {
                                changeListeners[changeSize] = null; // Let gc do its work
                            }
                        }

                        break;
                    }
                }
            }

            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> addListener(SubChangeListener listener, T currentValue) {
            if (subChangeListeners == null) {
                subChangeListeners = new SubChangeListener[] {listener};
                subChangeSize = 1;
            } else {
                final int oldCapacity = subChangeListeners.length;
                if (locked) {
                    final int newCapacity = (subChangeSize < oldCapacity) ? oldCapacity : (oldCapacity * 3) / 2 + 1;
                    subChangeListeners = Arrays.copyOf(subChangeListeners, newCapacity);
                } else if (subChangeSize == oldCapacity) {
                    subChangeSize = trim(subChangeSize, subChangeListeners);
                    if (subChangeSize == oldCapacity) {
                        final int newCapacity = (oldCapacity * 3) / 2 + 1;
                        subChangeListeners = Arrays.copyOf(subChangeListeners, newCapacity);
                    }
                }

                subChangeListeners[subChangeSize++] = listener;
            }

            if (subChangeSize == 1) {
                this.currentValue = currentValue;
            }

            ensurePeerSubChangeListener();
            return this;
        }

        @Override
        protected ProxyAsyncExpressionHelper<T> removeListener(SubChangeListener listener, T currentValue) {
            if (subChangeListeners != null) {
                for (int index = 0; index < subChangeSize; index++) {
                    if (subChangeListeners[index].equals(listener)) {
                        ProxyAsyncExpressionHelper<T> helper = getSingleListenerHelper(index, 0, 0, 0, 1);
                        if (helper != null) {
                            return helper;
                        }

                        if (subChangeSize == 1) {
                            subChangeListeners = null;
                            subChangeSize = 0;
                        } else {
                            final int numMoved = subChangeSize - index - 1;
                            final SubChangeListener[] oldListeners = subChangeListeners;
                            if (locked) {
                                subChangeListeners = new SubChangeListener[subChangeListeners.length];
                                System.arraycopy(oldListeners, 0, subChangeListeners, 0, index);
                            }

                            if (numMoved > 0) {
                                System.arraycopy(oldListeners, index + 1, subChangeListeners, index, numMoved);
                            }

                            subChangeSize--;
                            if (!locked) {
                                subChangeListeners[subChangeSize] = null; // Let gc do its work
                            }
                        }

                        break;
                    }
                }
            }

            return this;
        }

        @Override
        protected boolean validatesValue() {
            return changeSize > 0 || subChangeSize > 0;
        }

        @Override
        protected boolean containsBidirectionalBindingEndpoints() {
            if (changeSize == 0) {
                return false;
            }

            for (int i = 0; i < changeSize; i++) {
                if (changeListeners[i] instanceof BidirectionalBindingMarker) {
                    return true;
                }
            }

            return false;
        }

        @Override
        protected void fireValueChangedEvent(T newValue, boolean subChange) {
            final InvalidationListener[] curInvalidationList = invalidationListeners;
            final SubInvalidationListener[] curSubInvalidationList = subInvalidationListeners;
            final int curInvalidationSize = invalidationSize;
            final int curSubInvalidationSize = subInvalidationSize;
            final ChangeListener<? super T>[] curChangeList = changeListeners;
            final SubChangeListener[] curSubChangeList = subChangeListeners;
            final int curChangeSize = changeSize;
            final int curSubChangeSize = subChangeSize;

            try {
                locked = true;
                if (!subChange) {
                    for (int i = 0; i < curInvalidationSize; i++) {
                        try {
                            curInvalidationList[i].invalidated(observable);
                        } catch (Exception e) {
                            Thread.currentThread()
                                .getUncaughtExceptionHandler()
                                .uncaughtException(Thread.currentThread(), e);
                        }
                    }
                }

                for (int i = 0; i < curSubInvalidationSize; i++) {
                    try {
                        curSubInvalidationList[i].invalidated(observable, subChange);
                    } catch (Exception e) {
                        Thread.currentThread()
                            .getUncaughtExceptionHandler()
                            .uncaughtException(Thread.currentThread(), e);
                    }
                }

                if (curChangeSize > 0 || curSubChangeSize > 0) {
                    final T oldValue = currentValue;
                    currentValue = newValue;

                    if (!subChange) {
                        final boolean changed = !Objects.equals(currentValue, oldValue);
                        if (!changed) {
                            return;
                        }

                        for (int i = 0; i < curChangeSize; i++) {
                            try {
                                curChangeList[i].changed(observable, oldValue, currentValue);
                            } catch (Exception e) {
                                Thread.currentThread()
                                    .getUncaughtExceptionHandler()
                                    .uncaughtException(Thread.currentThread(), e);
                            }
                        }
                    }

                    for (int i = 0; i < curSubChangeSize; i++) {
                        try {
                            curSubChangeList[i].changed(observable, oldValue, currentValue, subChange);
                        } catch (Exception e) {
                            Thread.currentThread()
                                .getUncaughtExceptionHandler()
                                .uncaughtException(Thread.currentThread(), e);
                        }
                    }
                }
            } finally {
                locked = false;
            }
        }

        private ProxyAsyncExpressionHelper<T> getSingleListenerHelper(
                int index, int removeInvalidation, int removeSubInvalidation, int removeChange, int removeSubChange) {
            if (invalidationSize - removeInvalidation == 1
                    && subInvalidationSize - removeSubInvalidation == 0
                    && changeSize - removeChange == 0
                    && subChangeSize - removeSubChange == 0) {
                removePeerListeners();
                return new SingleInvalidation<>(
                    observable, peer, invalidationListeners[invalidationSize == 2 ? 1 - index : 0]);
            }

            if (invalidationSize - removeInvalidation == 0
                    && subInvalidationSize - removeSubInvalidation == 1
                    && changeSize - removeChange == 0
                    && subChangeSize - removeSubChange == 0) {
                removePeerListeners();
                return new SingleSubInvalidation<>(
                    observable, peer, subInvalidationListeners[subInvalidationSize == 2 ? 1 - index : 0]);
            }

            if (invalidationSize - removeInvalidation == 0
                    && subInvalidationSize - removeSubInvalidation == 0
                    && changeSize - removeChange == 1
                    && subChangeSize - removeSubChange == 0) {
                removePeerListeners();
                return new SingleChange<>(
                    observable, peer, currentValue, changeListeners[changeSize == 2 ? 1 - index : 0]);
            }

            if (invalidationSize - removeInvalidation == 0
                    && subInvalidationSize - removeSubInvalidation == 0
                    && changeSize - removeChange == 0
                    && subChangeSize - removeSubChange == 1) {
                removePeerListeners();
                return new SingleSubChange<>(
                    observable, peer, currentValue, subChangeListeners[subChangeSize == 2 ? 1 - index : 0]);
            }

            return null;
        }

        @Override
        protected void setPeer(@Nullable AsyncObservableValue<T> peer) {
            removePeerListeners();

            this.peer = peer;

            if (invalidationSize > 0) {
                ensurePeerInvalidationListener();
            }

            if (subInvalidationSize > 0) {
                ensurePeerSubInvalidationListener();
            }

            if (changeSize > 0) {
                ensurePeerChangeListener();
            }

            if (subChangeSize > 0) {
                ensurePeerSubChangeListener();
            }

            fireValueChangedEvent(observable.getValue(), false);
        }

        private void ensurePeerInvalidationListener() {
            boolean created = false;

            if (peerInvalidationListener == null) {
                peerInvalidationListener =
                    observable -> {
                        for (int i = 0; i < invalidationSize; ++i) {
                            invalidationListeners[i].invalidated(Generic.this.observable);
                        }
                    };

                created = true;
            }

            if (peer != null && created) {
                peer.addListener(peerInvalidationListener);
            }
        }

        private void ensurePeerSubInvalidationListener() {
            boolean created = false;

            if (peerSubInvalidationListener == null) {
                peerSubInvalidationListener =
                    (observable, subChange) -> {
                        for (int i = 0; i < subInvalidationSize; ++i) {
                            subInvalidationListeners[i].invalidated(Generic.this.observable, subChange);
                        }
                    };

                created = true;
            }

            if (peer instanceof AsyncSubObservable && created) {
                ((AsyncSubObservable)peer).addListener(peerSubInvalidationListener);
            }
        }

        private void ensurePeerChangeListener() {
            boolean created = false;

            if (peerChangeListener == null) {
                peerChangeListener =
                    (observable, oldValue, newValue) -> {
                        for (int i = 0; i < changeSize; ++i) {
                            changeListeners[i].changed(Generic.this.observable, oldValue, newValue);
                        }
                    };

                created = true;
            }

            if (peer != null && created) {
                peer.addListener(peerChangeListener);
            }
        }

        private void ensurePeerSubChangeListener() {
            boolean created = false;

            if (peerSubChangeListener == null) {
                peerSubChangeListener =
                    (observable, oldValue, newValue, subChange) -> {
                        for (int i = 0; i < subChangeSize; ++i) {
                            subChangeListeners[i].changed(Generic.this.observable, oldValue, newValue, subChange);
                        }
                    };

                created = true;
            }

            if (peer instanceof AsyncSubObservableValue && created) {
                ((AsyncSubObservableValue)peer).addListener(peerSubChangeListener);
            }
        }

        private void removePeerListeners() {
            if (peer != null) {
                if (peerInvalidationListener != null) {
                    peer.removeListener(peerInvalidationListener);
                }

                if (peerChangeListener != null) {
                    peer.removeListener(peerChangeListener);
                }

                if (peerSubInvalidationListener != null && peer instanceof AsyncSubObservable) {
                    ((AsyncSubObservable)peer).removeListener(peerSubInvalidationListener);
                }

                if (peerSubChangeListener != null && peer instanceof AsyncSubObservableValue) {
                    ((AsyncSubObservableValue)peer).removeListener(peerSubChangeListener);
                }
            }

            peerInvalidationListener = null;
            peerChangeListener = null;
            peerSubInvalidationListener = null;
            peerSubChangeListener = null;
        }
    }

}
