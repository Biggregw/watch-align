package com.watchalign.mobile.qc;

/**
 * Contract for a reusable measurement module.
 *
 * @param <I> module-specific input type
 */
public interface QcModule<I> {
    String id();
    QcModuleResult measure(I input);
}
