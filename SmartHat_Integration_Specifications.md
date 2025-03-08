{
  "protocol_version": "1.0",
  "ble": {
    "device_name": "SmartHat_2024",
    "service_uuid": "4fafc201-1fb5-459e-8fcc-c5c9c331914b",
    "characteristics": {
      "dust_sensor": {
        "uuid": "beb5483e-36e1-4688-b7f5-26a801d4acb6",
        "properties": ["read", "notify"],
        "format": {
          "type": "string",
          "value": "dust",
          "unit": "µg/m³",
          "range": [0, 1000]
        }
      },
      "noise_sensor": {
        "uuid": "beb5483e-36e1-4688-b7f5-26a801d4acb7",
        "properties": ["read", "notify"],
        "format": {
          "type": "string",
          "value": "noise",
          "unit": "dB",
          "range": [0, 120]
        }
      }
    },
    "connection": {
      "timeout": 10,
      "retry_attempts": 3,
      "reconnection_strategy": "exponential_backoff"
    }
  },
  "data_format": {
    "json_keys": ["sensor", "value"],
    "example_payload": {
      "sensor": "dust",
      "value": 42.5
    },
    "strict_mode": false
  },
  "notifications": {
    "thresholds": {
      "dust": 50.0,
      "noise": 85.0
    },
    "cooldown": 30,
    "error_handling": {
      "crc_check": true,
      "error_codes": {
        "sensor_disconnected": 1001,
        "invalid_data": 1002,
        "connection_timeout": 1003
      }
    }
  },
  "demo_features": {
    "test_mode": true,
    "simulated_payloads": [
      {
        "sensor": "dust",
        "value": 55.0
      },
      {
        "sensor": "noise",
        "value": 90.0
      }
    ],
    "manual_triggers": {
      "triple_tap_error": true,
      "swipe_threshold_trigger": true,
      "long_press_reset": true
    }
  },
  "logging": {
    "enable_debug_logs": true,
    "log_levels": ["DEBUG", "VERBOSE"],
    "metrics": {
      "track_connection_success": true,
      "track_data_latency": true
    }
  },
  "battery_optimization": {
    "ignore_optimizations": true
  },
  "bluetooth_state_monitoring": {
    "handle_bluetooth_toggle": true
  }
}
