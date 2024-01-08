import RPi.GPIO as GPIO
import time
import paho.mqtt.client as mqtt

# Set GPIO numbering mode
GPIO.setmode(GPIO.BOARD)

# Set pin 11 as an output, and set servo1 as pin 11 as PWM
GPIO.setup(5, GPIO.OUT)
servo1 = GPIO.PWM(5, 50)  # Note 11 is pin, 50 = 50Hz pulse

# Start PWM running, but with a value of 0 (pulse off)
servo1.start(0)
print("Waiting for 2 seconds")
time.sleep(2)

# MQTT settings
MQTT_BROKER = "test.mosquitto.org"
MQTT_TOPIC = "gate"

def on_connect(client, userdata, flags, rc):
    print("Connected with result code "+str(rc))
    client.subscribe(MQTT_TOPIC)

def on_message(client, userdata, msg):
    control_command = msg.payload.decode()
    print("Received control command: " + control_command)

    if control_command == "open":
        open_gate()
    elif control_command == "close":
        close_gate()

# MQTT client setup
mqtt_client = mqtt.Client()
mqtt_client.on_connect = on_connect
mqtt_client.on_message = on_message
mqtt_client.connect(MQTT_BROKER, 1883, 60)

def open_gate():
    print("Opening the gate to 90 degrees")
    servo1.ChangeDutyCycle(7)  # Adjust duty cycle for 90 degrees
    time.sleep(2)
    close_gate()

def close_gate():
    print("Closing the gate smoothly")
    start_time = time.time()
    while time.time() - start_time <= 10:
        duty_cycle = 7 - ((time.time() - start_time) / 10) * 5  # Linearly decrease duty cycle
        servo1.ChangeDutyCycle(duty_cycle)
        time.sleep(0.1)

# MQTT loop
mqtt_client.loop_start()

# Keep the script running
try:
    while True:
        time.sleep(1)
        
except KeyboardInterrupt:
    print("Cleaning up GPIO")
    servo1.stop()
    GPIO.cleanup()
    mqtt_client.disconnect()
    mqtt_client.loop_stop()
    print("Gate closed smoothly. Goodbye")

