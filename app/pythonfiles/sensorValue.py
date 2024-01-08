import RPi.GPIO as GPIO
import time
import paho.mqtt.client as mqtt

# Set MQTT broker and topic
broker = "test.mosquitto.org"   # Broker
pub_topic = "parkingStatus"
      # send messages to this topic

# IR Sensor pin
IR_SENSOR_PIN = 3

# Configure GPIO
GPIO.setmode(GPIO.BOARD)
GPIO.setup(IR_SENSOR_PIN, GPIO.IN)
GPIO.setwarnings(False)


############### MQTT section ##################

# when connecting to mqtt do this;
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Connection established. Code: " + str(rc))
    else:
        print("Connection failed. Code: " + str(rc))

def on_publish(client, userdata, mid):
    print("Published: " + str(mid))

def on_disconnect(client, userdata, rc):
    if rc != 0:
        print("Unexpected disconnection. Code: ", str(rc))
    else:
        print("Disconnected. Code: " + str(rc))

def on_log(client, userdata, level, buf):  # Message is in buf
    print("MQTT Log: " + str(buf))

############### Sensor section ##################

def get_ir_sensor_value():
    # Read IR sensor value (adjust this based on your sensor specifications)
    return GPIO.input(IR_SENSOR_PIN)

# Connect functions for MQTT
client = mqtt.Client()
client.on_connect = on_connect
client.on_disconnect = on_disconnect
client.on_publish = on_publish

client.on_log = on_log

# Connect to MQTT
print("Attempting to connect to broker " + broker)
client.connect(broker)  # Broker address, port, and keepalive
client.loop_start()
status = "";
# Loop that publishes message

while True:
    if not client.is_connected():
        print("Reconnecting to broker " + broker)
        client.connect(broker)

    ir_sensor_value = get_ir_sensor_value()
    #print(ir_sensor_value)

    if ir_sensor_value == GPIO.HIGH:
        print("Available")
        status = "Available,W0iVkjLpXYWke5tkmeix,JuP2Hk7HDoTHUCY3t3Kr"
        time.sleep(1.5)

    else:
        print("Occupied,W0iVkjLpXYWke5tkmeix,JuP2Hk7HDoTHUCY3t3Kr")
        status = "Occupied,W0iVkjLpXYWke5tkmeix,JuP2Hk7HDoTHUCY3t3Kr"
    # Publish to MQTT
    data_to_send = str(status)
    client.publish(pub_topic, data_to_send)

    time.sleep(2.0)
	
# Cleanup GPIO
GPIO.cleanup()


