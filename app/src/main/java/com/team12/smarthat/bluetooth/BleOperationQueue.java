package com.team12.smarthat.bluetooth;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.team12.smarthat.utils.Constants;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

//proper callbacks before starting the next operation.

public class BleOperationQueue {
    private Queue<Runnable> operationQueue = new ConcurrentLinkedQueue<>();
    private boolean isProcessing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Add a new operation to the queue and process if if no operations are in progress
     * @param operation The operation to queue
     */
    public synchronized void queue(Runnable operation) {
        Log.d(Constants.TAG_BLUETOOTH, "Queueing BLE operation");
        operationQueue.add(operation);
        if (!isProcessing) {
            processQueue();
        }
    }

    /**
     * Signal that the current operation has completed and move to the next one
     */
    public synchronized void operationComplete() {
        Log.d(Constants.TAG_BLUETOOTH, "BLE operation completed");
        isProcessing = false;
        processQueue();
    }

    /**
     * Process the next operation in the queue if any
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
        
        // Execute on main thread to avoid threading issues with Bluetooth operations
        handler.post(() -> {
            try {
                Log.d(Constants.TAG_BLUETOOTH, "Executing BLE operation");
                operation.run();
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error executing BLE operation: " + e.getMessage(), e);
                isProcessing = false;
                processQueue(); // Process next operation
            }
        });
    }
} 