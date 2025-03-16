package com.team12.smarthat.bluetooth.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.team12.smarthat.utils.Constants;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * queue for ble operations for proper order 
 * callbacks before starting the next operation.
 * 
 * used this since ble operations on android need to be executed sequentially
 */
public class BleOperationQueue {
    private Queue<Runnable> operationQueue = new ConcurrentLinkedQueue<>();
    private boolean isProcessing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * add a new operation to the queue and process if if no operations are in progress
     * @param operation the operation to queue
     */
    public synchronized void queue(Runnable operation) {
        Log.d(Constants.TAG_BLUETOOTH, "Queueing BLE operation");
        operationQueue.add(operation);
        if (!isProcessing) {
            processQueue();
        }
    }
    
    /**
     * alias for queue method to maintain compatibility with code that uses enqueue
     * @param operation the operation to enqueue
     */
    public synchronized void enqueue(Runnable operation) {
        queue(operation);
    }

    /**
     * signal that the current operation has completed and move to the next one
     */
    public synchronized void operationComplete() {
        Log.d(Constants.TAG_BLUETOOTH, "BLE operation completed");
        isProcessing = false;
        processQueue();
    }

    /**
     * process the next operation in the queue if any
     */
    private synchronized void processQueue() {
        if (operationQueue.isEmpty() || isProcessing) {
            if (operationQueue.isEmpty()) {
                Log.d(Constants.TAG_BLUETOOTH, "BLE operation queue empty");
            }
            return;
        }
        
        isProcessing = true;
        Runnable operation = operationQueue.poll();
        
        // execute on main thread to avoid threading issues with bluetooth operations
        handler.post(() -> {
            try {
                Log.d(Constants.TAG_BLUETOOTH, "Executing BLE operation");
                operation.run();
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error executing BLE operation: " + e.getMessage(), e);
                isProcessing = false;
                processQueue(); // process next operation
            }
        });
    }

    /**
     * cancel all pending operations and clear the queue.
     * call wjem cleaning up
     */
    public synchronized void cancelAllOperations() {
        Log.d(Constants.TAG_BLUETOOTH, "Cancelling all BLE operations and clearing the queue");
        operationQueue.clear();
        isProcessing = false;
        
        // remove any pending operations from the handler
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * check if the operation queue is empty
     * test only
     * @return true if the queue is empty, false otherwise
     */
    @VisibleForTesting
    public boolean isQueueEmpty() {
        return operationQueue.isEmpty();
    }
} 