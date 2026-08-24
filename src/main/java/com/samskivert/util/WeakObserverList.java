//
// samskivert library - useful routines for java programs
// Copyright (C) 2001-2012 Michael Bayne, et al.
// http://github.com/samskivert/samskivert/blob/master/COPYING

package com.samskivert.util;

import java.lang.ref.WeakReference;

import java.util.HashSet;
import java.util.Set;

/**
 * An {@link ObserverList} equivalent that does not prevent added observers from being
 * garbage-collected.
 */
public class WeakObserverList<T> extends ObserverList<T>
{
    /**
     * Creates a list with {@link ObserverList.Policy#SAFE_IN_ORDER} notification policy.
     */
    public static <T> WeakObserverList<T> newSafeInOrder ()
    {
        return newList(Policy.SAFE_IN_ORDER);
    }

    /**
     * Creates a list with {@link ObserverList.Policy#FAST_UNSAFE} notification policy.
     */
    public static <T> WeakObserverList<T> newFastUnsafe ()
    {
        return newList(Policy.FAST_UNSAFE);
    }

    /**
     * Creates a weak observer list with the specified notification policy.
     */
    public static <T> WeakObserverList<T> newList (Policy notifyPolicy)
    {
        return new WeakObserverList<T>(notifyPolicy);
    }

    @Override public boolean add (int index, T element)
    {
        // no maybePrune() here: pruning would shift the meaning of the caller's index
        return _delegate.add(index, new WeakReference<T>(element));
    }

    @Override public boolean add (T element)
    {
        maybePrune();
        return _delegate.add(new WeakReference<T>(element));
    }

    @Override public boolean remove (T element)
    {
        return _delegate.remove(new WeakReference<T>(element));
    }

    @Override public void apply (ObserverOp<T> obop)
    {
        _derefOp.init(obop);
        _delegate.apply(_derefOp);
    }

    @Override public int size ()
    {
        return _delegate.size();
    }

    @Override public void clear ()
    {
        _delegate.clear();
    }

    @Override public WeakObserverList<T> setCheckDuplicates (boolean checkDuplicates)
    {
        _delegate.setCheckDuplicates(checkDuplicates);
        return this;
    }

    /**
     * Removes all garbage-collected observers from the list.
     */
    public void prune ()
    {
        _pruneThreshold = Math.max(MIN_PRUNE_THRESHOLD, 2 * _delegate.compact());
    }

    /**
     * Prunes when the list has doubled since the last prune, keeping {@link #add(Object)}
     * amortized constant. Notification is the only other point at which collected references
     * are removed, so a rarely-notified list would otherwise grow without bound and (with the
     * SAFE_IN_ORDER policy) pay an ever-larger array copy on every add.
     */
    protected void maybePrune ()
    {
        if (_delegate.size() >= _pruneThreshold) {
            prune();
        }
    }

    protected WeakObserverList (Policy notifyPolicy)
    {
        _delegate = new WrappedList<T>(notifyPolicy);
    }

    /**
     * An operation that resolves a reference and applies a wrapped op.
     */
    protected static class DerefOp<T> implements ObserverOp<WeakReference<T>>
    {
        /** (Re)initializes this op with a reference to the wrapped op. */
        public void init (ObserverOp<T> op) {
            _op = op;
        }

        // documentation inherited from interface ObserverOp
        public boolean apply (WeakReference<T> ref) {
            T observer = ref.get();
            return observer != null && _op.apply(observer);
        }

        @Override public String toString () {
            return "DerefOp:" + _op;
        }

        /** The wrapped op. */
        protected ObserverOp<T> _op;
    }

    /**
     * ObserverList extension that dereferences elements when searching for a value.
     */
    protected static class WrappedList<T> extends ObserverList.Impl<WeakReference<T>>
    {
        public WrappedList (Policy notifyPolicy) {
            super(notifyPolicy);
        }

        @Override protected int indexOf (WeakReference<T> ref) {
            T value = ref.get();
            for (int ii = 0, ll = _list.size(); ii < ll; ii++) {
                if (_list.get(ii).get() == value) { return ii; }
            }
            return -1;
        }

        @Override protected Object observerForLog (WeakReference<T> ref) {
            return ref.get();
        }

        /**
         * Removes all collected references in a single pass and returns the new size.
         */
        public int compact () {
            Set<WeakReference<T>> dead = null;
            for (WeakReference<T> ref : _list) {
                if (ref.get() == null) {
                    if (dead == null) {
                        dead = new HashSet<WeakReference<T>>();
                    }
                    dead.add(ref);
                }
            }
            if (dead != null) {
                _list.removeAll(dead);
            }
            return _list.size();
        }
    }

    /** A delegate list that contains weak reference wrapped elements. */
    protected WrappedList<T> _delegate;

    /** The wrapper op. */
    protected DerefOp<T> _derefOp = new DerefOp<T>();

    /** Prune when the delegate reaches this size: twice the live count as of the last prune. */
    protected int _pruneThreshold = MIN_PRUNE_THRESHOLD;

    /** The minimum size at which pruning kicks in. */
    protected static final int MIN_PRUNE_THRESHOLD = 32;
}
