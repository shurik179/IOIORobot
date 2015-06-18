package org.sigmacamp.ioiorobot;


import android.location.Location;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.IOIO.VersionType;
import ioio.lib.api.PulseInput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;



public class IOIOThread extends BaseIOIOLooper {
    private DigitalOutput led0, rightLed, leftLed;
    private Motor rightMotor, leftMotor;
    private Sonar leftSonar, rightSonar;
    public MainActivity parent;
    boolean heartBeat=false; //used for built-in LED, as heartbeat
    enum Mode {STOP, GO, OBSTACLE_RIGHT, OBSTACLE_LEFT};
    Mode mode;
    Gps gps;
    Compass compass;
    //constructor
    IOIOThread(MainActivity p) {
        super();
        this.parent = p;
    }

    /**
     * Called when the IOIO is disconnected.
     */
    @Override
    public void disconnected() {
        parent.popup("IOIO disconnected");
    }

    /**
     * Called when the IOIO is connected, but has an incompatible firmware version.
     */
    @Override
    public void incompatible() {
        parent.popup("IOIO: Incompatible firmware version!");
    }

    /**
     * Setup - called when the IOIO is connected
     */
    @Override
    protected void setup() throws ConnectionLostException {
        //print connection information
        parent.popup(String.format("IOIO connected\n" +
                        "IOIOLib: %s\n" +
                        "Application firmware: %s\n" +
                        "Bootloader firmware: %s\n" +
                        "Hardware: %s",
                ioio_.getImplVersion(VersionType.IOIOLIB_VER),
                ioio_.getImplVersion(VersionType.APP_FIRMWARE_VER),
                ioio_.getImplVersion(VersionType.BOOTLOADER_VER),
                ioio_.getImplVersion(VersionType.HARDWARE_VER)));
        //set up various LEDs etc
        led0 = ioio_.openDigitalOutput(0, true);
        leftLed = ioio_.openDigitalOutput(40, true);
        rightLed = ioio_.openDigitalOutput(39, true);
        rightMotor=new Motor(13,14);
        leftMotor=new Motor(11,12);
        leftSonar=new Sonar(7,6);//trigger pin=7, echo pin=6
        rightSonar=new Sonar(9,10);//trigger pin=7, echo pin=6
        gps=parent.gps;
        compass=parent.compass;
    }

    /**
     * Called repetitively while the IOIO is connected.
     */
    @Override
    public void loop() throws ConnectionLostException, InterruptedException {
        float leftDistance, rightDistance, distanceToTarget, angle_error;
        Location homeLocation;
        boolean obstacleleft, obstacleright;
        //pulse the built in LED
        led0.write(heartBeat);
        heartBeat=!heartBeat;
        //get distances and other info
        leftDistance =leftSonar.getDistance();
        rightDistance =rightSonar.getDistance();
        obstacleleft=(leftDistance<60);
        obstacleright=(rightDistance<60);
        leftLed.write(obstacleleft);
        rightLed.write(obstacleright);
        homeLocation=parent.homeLocation;
        if (homeLocation==null) {
            distanceToTarget=10000;
        } else  {
            distanceToTarget=gps.distanceTo(homeLocation);
        }
        // set the new mode -  based on previous mode and new info
        if (!parent.startButton.isChecked() || homeLocation==null || distanceToTarget<2.00f ){
            // robot is turned off or home location not set or we have reached destination
            mode=Mode.STOP;
        } else {
            //we are good to go - let us set mode depending on previous mode and other data
            switch (mode) {
                case STOP:
                    mode=Mode.GO;
                    break;
                case GO:
                    if (obstacleleft||obstacleright){
                        //we see an obstacle! which one is closer?
                        if (leftDistance<rightDistance) {
                            mode=Mode.OBSTACLE_LEFT;
                        } else {
                            mode=Mode.OBSTACLE_RIGHT;
                        }
                    }
                    //otherwise, do not change the mode
                    break;
                case OBSTACLE_LEFT:
                    if (!obstacleleft) {
                        //no obstacle to the left!
                        mode=Mode.GO;
                    }
                    break;
                case OBSTACLE_RIGHT:
                    if (!obstacleright){
                        mode=Mode.GO;
                    }
                    break;
            }
        }
        //set the motion, depending on the mode.
        switch (mode) {
            case STOP:
                stopMotors();
                break;
            case GO:
                //get the angle error between desired direction to target and actual heading
                //+=we need to turn right, -=we need to turn left
                angle_error=gps.bearingTo(homeLocation)-compass.getAzimut();
                //make sure it is between -180 and 180
                if (angle_error<-180) {
                    angle_error+=360;
                } else if (angle_error>180) {
                    angle_error-=360;
                }
                //set the motors
                setMotors(80, 0.4f*angle_error);
                break;
            case OBSTACLE_LEFT:
                //continue turning right, with a little moving forward
                setMotors(20, 50);
                break;
            case OBSTACLE_RIGHT:
                //continue turning left, with a little moving forward
                setMotors(20, -50);
                break;
        }


        Thread.sleep(100);
    }
    private class Motor {
        private PwmOutput pin1, pin2;
        //constructor
        //requires 2 arguments: pin numbers
        public Motor( int  n1, int n2) throws ConnectionLostException {
            pin1 = ioio_.openPwmOutput(n1, 5000); //opens as PWM output, frequency =5Khz
            pin2 = ioio_.openPwmOutput(n2, 5000);
        }

        public void setPower(float power) throws ConnectionLostException {
            //make sure power is between -100 ... 100
            if (power > 100) {
                power = 100;
            } else if (power < -100) {
                power = -100;
            }
            if (power > 0) {
                pin1.setDutyCycle(power / 100);
                pin2.setDutyCycle(0);
            } else {
                pin1.setDutyCycle(0);
                pin2.setDutyCycle(Math.abs(power) / 100);
            }
        }

        public void stop() throws ConnectionLostException {
            pin1.setDutyCycle(0);
            pin2.setDutyCycle(0);
        }
    }

    /*
  Class sonar. Uses ultrasonic sensor to measure distance.
 */
    private class Sonar {
        private DigitalOutput triggerPin;
        private PulseInput echoPin;
        //constructor
        //requires 2 arguments: trigger pin number, echo pin number (echo must be 5V tolerant)
        public Sonar(int  trigger, int echo) throws ConnectionLostException{
            echoPin = ioio_.openPulseInput(echo, PulseInput.PulseMode.POSITIVE);
            triggerPin = ioio_.openDigitalOutput(trigger);
        }
        //measure distance, in cm. Returns -1 if connection lost.
        public float getDistance() throws ConnectionLostException, InterruptedException {
            int echoMseconds;
            triggerPin.write(false);
            Thread.sleep(5);
            // set trigger pin to HIGH for 1 ms
            triggerPin.write(true);
            Thread.sleep(1);
            triggerPin.write(false);
            //get response time in microseconds
            echoMseconds = (int) (echoPin.getDuration() * 1000 * 1000);
            //convert microseconds to cm
            return ((float) echoMseconds / 58);
        }
    }

   private void stopMotors() throws ConnectionLostException{
       leftMotor.stop();
       rightMotor.stop();
   }
    //set motors. Power is total moving forward speed, turn is measure of turn (clockwise=+)
    private void setMotors(float power, float turn) throws ConnectionLostException{
        leftMotor.setPower(power + turn);
        rightMotor.setPower(power - turn);
    }

}
