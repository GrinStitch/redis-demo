package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取锁
     * @param time
     * @return
     */
    boolean tryLock(Long time);

    /**
     * 释放锁
     */
    void unlock();
}
