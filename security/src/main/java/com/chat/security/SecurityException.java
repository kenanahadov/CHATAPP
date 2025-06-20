
package com.chat.security;

public class SecurityException extends Exception {
    public SecurityException(String msg, Throwable cause) { super(msg, cause); }
    public SecurityException(String msg) { super(msg); }
}
