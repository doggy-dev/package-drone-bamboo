/*
 * Copyright Gemtec GmbH 2009-2019
 *
 * Erstellt am: 11.01.2019 10:21:31
 * Erstellt von: Christian Schwarz 
 */
package eu.gemtec.packagedrone.deploy.impl;

/**
 * @author Christian Schwarz
 *
 */
public class UploadException extends Exception {

	private static final long serialVersionUID = 7820235011752570082L;

	public UploadException() {}

	public UploadException(String message) {
		super(message);
	}

	public UploadException(Throwable cause) {
		super(cause);
	}

	public UploadException(	String message,
							Throwable cause) {
		super(message, cause);
	}
}
