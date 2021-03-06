package com.diozero.internal.provider.pigpioj;

/*
 * #%L
 * Device I/O Zero - pigpioj provider
 * %%
 * Copyright (C) 2016 diozero
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */


import org.pmw.tinylog.Logger;

import com.diozero.api.DigitalInputEvent;
import com.diozero.api.GpioEventTrigger;
import com.diozero.api.GpioPullUpDown;
import com.diozero.internal.spi.AbstractInputDevice;
import com.diozero.internal.spi.DeviceFactoryInterface;
import com.diozero.internal.spi.GpioDigitalInputDeviceInterface;
import com.diozero.pigpioj.PigpioCallback;
import com.diozero.pigpioj.PigpioGpio;
import com.diozero.util.RuntimeIOException;

public class PigpioJDigitalInputDevice extends AbstractInputDevice<DigitalInputEvent> implements GpioDigitalInputDeviceInterface, PigpioCallback {
	private int gpio;
	private int edge;

	public PigpioJDigitalInputDevice(String key, DeviceFactoryInterface deviceFactory, int gpio,
			GpioPullUpDown pud, GpioEventTrigger trigger) throws RuntimeIOException {
		super(key, deviceFactory);
		
		switch (trigger) {
		case RISING:
			edge = PigpioGpio.RISING_EDGE;
			break;
		case FALLING:
			edge = PigpioGpio.FALLING_EDGE;
			break;
		case BOTH:
			edge = PigpioGpio.EITHER_EDGE;
			break;
		case NONE:
		default:
			edge = PigpioGpio.NO_EDGE;
		}
		int pigpio_pud = PigpioJDeviceFactory.getPigpioJPullUpDown(pud);
		
		int rc = PigpioGpio.setMode(gpio, PigpioGpio.MODE_PI_INPUT);
		if (rc < 0) {
			throw new RuntimeIOException("Error calling PigpioGpio.setMode(), response: " + rc);
		}
		rc = PigpioGpio.setPullUpDown(gpio, pigpio_pud);
		if (rc < 0) {
			throw new RuntimeIOException("Error calling PigpioGpio.setPullUpDown(), response: " + rc);
		}
		
		this.gpio = gpio;
	}

	@Override
	public int getGpio() {
		return gpio;
	}

	@Override
	public void closeDevice() throws RuntimeIOException {
		Logger.debug("closeDevice()");
		// No GPIO close method in pigpio
		removeListener();
	}

	@Override
	public boolean getValue() throws RuntimeIOException {
		int rc = PigpioGpio.read(gpio);
		if (rc < 0) {
			throw new RuntimeIOException("Error calling PigpioGpio.read(), response: " + rc);
		}
		return rc == 1;
	}

	@Override
	public void setDebounceTimeMillis(int debounceTime) {
		throw new UnsupportedOperationException("Debounce not supported in pigpioj");
	}

	@Override
	public void enableListener() {
		disableListener();
		if (edge == PigpioGpio.NO_EDGE) {
			Logger.warn("Edge was configured to be NO_EDGE, no point adding a listener");
			return;
		}
		
		int rc = PigpioGpio.setISRFunc(gpio, edge, -1, this);
		if (rc < 0) {
			throw new RuntimeIOException("Error calling PigpioGpio.setISRFunc(), response: " + rc);
		}
	}

	@Override
	public void disableListener() {
		int rc = PigpioGpio.setISRFunc(gpio, PigpioGpio.EITHER_EDGE, -1, null);
		if (rc < 0) {
			throw new RuntimeIOException("Error calling PigpioGpio.setISRFunc(), response: " + rc);
		}
	}

	@Override
	public void callback(int pin, boolean value, long epochTime, long nanoTime) {
		if (pin != gpio) {
			Logger.error("Error, got a callback for the wrong pin ({}), was expecting {}",
					Integer.valueOf(pin), Integer.valueOf(gpio));
		}
		
		valueChanged(new DigitalInputEvent(pin, epochTime, nanoTime, value));
	}
}
