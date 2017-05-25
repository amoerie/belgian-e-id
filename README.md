# belgian-e-id

## Installation instructions for Windows

1. Open the workspace folder
2. Compile the Java source code by running the "0. Build.cmd" script (note that this does not build the JavaCard application)
3. Startup the governmental time server by running the "1. StartGovernmentServer.cmd" script. This opens the gov server running on port 4444.
4. Startup the service provider software by running the "2. StartServiceProvider.cmd" script. 
5. Choose a domain, service and number of attributes and press the "Init provider" button. This opens the service provider running on port 8888.
6. Ensure the Java Card applet is running at port 9025. (We did not find a better way than starting the JCWDE simulator...)
7. Startup the middleware by running the "3. StartMiddleware.cmd" script. 
