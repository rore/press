package press;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import play.PlayPlugin;
import play.mvc.Router;
import play.vfs.VirtualFile;
import press.io.FileIO;
import press.io.PressFileGlobber;

public class Plugin extends PlayPlugin {
    static ThreadLocal<JSCompressor> jsCompressor = new ThreadLocal<JSCompressor>();
    static ThreadLocal<CSSCompressor> cssCompressor = new ThreadLocal<CSSCompressor>();
    static ThreadLocal<Boolean> errorOccurred = new ThreadLocal<Boolean>();
    static ThreadLocal<Map<String, Boolean>> jsFiles = new ThreadLocal<Map<String, Boolean>>();
    static ThreadLocal<Map<String, Boolean>> cssFiles = new ThreadLocal<Map<String, Boolean>>();
    static final int MAX_SRC_LEN = 600;
    
    @Override
    public void onApplicationStart() {
        // Read the config each time the application is restarted
        PluginConfig.readConfig();

        // Clear the cache
        JSCompressor.clearCache();
        CSSCompressor.clearCache();
    }

    @Override
    public void beforeActionInvocation(Method actionMethod) {
        // Before each action, reinitialize variables
        jsCompressor.set(new JSCompressor());
        cssCompressor.set(new CSSCompressor());
        errorOccurred.set(false);
        jsFiles.set(new HashMap<String, Boolean>());
        cssFiles.set(new HashMap<String, Boolean>());
    }

    /**
     * Add a single JS file to compression
     */
    public static String addSingleJS(String fileName, String dir, boolean compress) {
        VirtualFile srcFile = checkJSFileExists(fileName, dir);
        JSCompressor compressor = jsCompressor.get();
        String src = null;
        if (compress && performCompression()) {
            String requestKey = compressor.compressedSingleFileUrl(fileName, dir);
            if (PluginConfig.isInMemoryStorage()) {
                src = getSingleCompressedJSUrl(requestKey);
            } else {
                src = requestKey;
            }
        } else {
        	if (null != dir && !dir.isEmpty())
        		src = dir + fileName;
        	else
        		src = compressor.srcDir + fileName;
        	if (press.PluginConfig.cacheBuster){
        		src += "?" + srcFile.lastModified();
        	}

        }

        return getScriptTag(src);
    }

    /**
     * Add a single CSS file to compression
     */
    public static String addSingleCSS(String fileName) {
        checkCSSFileExists(fileName);
        CSSCompressor compressor = cssCompressor.get();
        String src = null;
        if (performCompression()) {
            String requestKey = compressor.compressedSingleFileUrl(fileName);
            if (PluginConfig.isInMemoryStorage()) {
                src = getSingleCompressedCSSUrl(requestKey);
            } else {
                src = requestKey;
            }
        } else {
            src = compressor.srcDir + fileName;
        }

        return getLinkTag(src);
    }

    /**
     * Adds the given source file(s) to the JS compressor, returning the file
     * signature to be output in HTML
     */
    public static String addJS(String src, boolean compress, int pos) {
        JSCompressor compressor = jsCompressor.get();
        String baseUrl = compressor.srcDir;
        String result = "";
        if (press.PluginConfig.serverFarm && pos < 0)
        	throw new PressException("serverFarm enabled and script position not specified in tag: " + src);
        for (String fileName : PressFileGlobber.getResolvedFiles(src, baseUrl)) {
            checkForJSDuplicates(fileName);

            if (performCompression()) {
                result += compressor.add(fileName, compress, pos) + "\n";
            } else {
                result += getScriptTag(baseUrl + fileName);
            }
        }

        return result;
    }

    /**
     * Adds the given source file(s) to the CSS compressor, returning the file
     * signature to be output in HTML
     */
    public static String addCSS(String src, boolean compress, int pos) {
        CSSCompressor compressor = cssCompressor.get();
        String baseUrl = compressor.srcDir;
        String result = "";
        if (press.PluginConfig.serverFarm && pos < 0)
        	throw new PressException("serverFarm enabled and stylesheet position not specified in tag: "+ src);
        for (String fileName : PressFileGlobber.getResolvedFiles(src, baseUrl)) {
            checkForCSSDuplicates(fileName);

            if (performCompression()) {
                result += compressor.add(fileName, compress, pos) + "\n";
            } else {
                result += getLinkTag(baseUrl + fileName);
            }
        }

        return result;
    }

    /**
     * Outputs the tag indicating where the compressed CSS should be included.
     */
    public static String compressedCSSTag() {
        if (performCompression()) {
            String requestKey = cssCompressor.get().closeRequest();
            return compressedCSSTag(requestKey);
        }
        return "";
    }

    public static String compressedCSSTag(String requestKey) {
    	 if (performCompression()) {
         	// if the src url is too long, break it
         	if (press.PluginConfig.serverFarm && requestKey.length() > MAX_SRC_LEN){
         		int pos = requestKey.lastIndexOf(",", MAX_SRC_LEN);
         		if (pos > -1){
         			String first = requestKey.substring(0, pos) + ".css";
         			String rest = requestKey.substring(pos+1);
         			String script = getScriptTag(getCompressedCSSUrl(first));
         			if(null == rest || rest.length() == 0)
         				return script;
         			return script + compressedCSSTag(rest);
         		}
         	}
             return getScriptTag(getCompressedCSSUrl(requestKey));
         }
         return "";
    }

    /**
     * Outputs the tag indicating where the compressed JS should be included.
     */
    public static String compressedJSTag() {
        if (performCompression()) {
            String requestKey = jsCompressor.get().closeRequest();
            return compressedJSTag(requestKey);
        }
        return "";
    }

    public static String compressedJSTag(String requestKey) {
        if (performCompression()) {
        	// if the src url is too long, break it
        	if (press.PluginConfig.serverFarm && requestKey.length() > MAX_SRC_LEN){
        		int pos = requestKey.lastIndexOf(",", MAX_SRC_LEN);
        		if (pos > -1){
        			String first = requestKey.substring(0, pos) + ".js";
        			String rest = requestKey.substring(pos+1);
        			String script = getScriptTag(getCompressedJSUrl(first));
        			if(null == rest || rest.length() == 0)
        				return script;
        			return script + compressedJSTag(rest);
        		}
        	}
            return getScriptTag(getCompressedJSUrl(requestKey));
        }
        return "";
    }

    @Override
    public void afterActionInvocation() {
        // At the end of the action, save the list of files that will be
        // associated with this request
        if (jsCompressor.get() != null && cssCompressor.get() != null && performCompression()) {
            jsCompressor.get().saveFileList();
            cssCompressor.get().saveFileList();
        }
    }

    @Override
    public void onInvocationException(Throwable e) {
        errorOccurred.set(true);
    }

    /**
     * Indicates whether or not an error has occurred
     */
    public static boolean hasErrorOccurred() {
        return errorOccurred.get() == null || errorOccurred.get();
    }

    /**
     * Indicates whether or not to compress files
     */
    public static boolean performCompression() {
        return PluginConfig.enabled && !hasErrorOccurred();
    }

    /**
     * Check if the given JS file exists.
     */
    public static VirtualFile checkJSFileExists(String fileName) {
        return JSCompressor.checkJSFileExists(fileName);
    }

    public static VirtualFile checkJSFileExists(String fileName, String dir) {
        return JSCompressor.checkJSFileExists(fileName, dir);
    }

    /**
     * Check if the given CSS file exists.
     */
    public static void checkCSSFileExists(String fileName) {
        CSSCompressor.checkCSSFileExists(fileName);
    }

    /**
     * Check if the given JS file has already been included.
     */
    public static void checkForJSDuplicates(String fileName) {
        checkJSFileExists(fileName);
        checkForDuplicates(jsFiles.get(), fileName, JSCompressor.FILE_TYPE, JSCompressor.TAG_NAME);
    }

    /**
     * Check if the given CSS file has already been included.
     */
    public static void checkForCSSDuplicates(String fileName) {
        checkCSSFileExists(fileName);
        checkForDuplicates(cssFiles.get(), fileName, CSSCompressor.FILE_TYPE,
                CSSCompressor.TAG_NAME);
    }

    private static void checkForDuplicates(Map<String, Boolean> files, String fileName,
            String fileType, String tagName) {

        if (!files.containsKey(fileName)) {
            files.put(fileName, true);
            return;
        }

        throw new DuplicateFileException(fileType, fileName, tagName);
    }

    private static String getSingleCompressedCSSUrl(String requestKey) {
        return getCompressedUrl("press.Press.getSingleCompressedCSS", requestKey);
    }

    private static String getSingleCompressedJSUrl(String requestKey) {
        return getCompressedUrl("press.Press.getSingleCompressedJS", requestKey);
    }

    private static String getCompressedCSSUrl(String requestKey) {
        return getCompressedUrl("press.Press.getCompressedCSS", requestKey);
    }

    private static String getCompressedJSUrl(String requestKey) {
        return getCompressedUrl("press.Press.getCompressedJS", requestKey);
    }

    private static String getCompressedUrl(String action, String requestKey) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("key", FileIO.escape(requestKey));
        return Router.reverse(action, params).url;
    }

    /**
     * Returns a script tag which can be used to output uncompressed JavaScript
     * tags within the HTML.
     */
    private static String getScriptTag(String src) {
    	StringBuilder sb = new StringBuilder();
    	sb.append("<script src=\"");
    	if (null != press.PluginConfig.contentHostingDomain && press.PluginConfig.contentHostingDomain.length() > 0)
    		sb.append(press.PluginConfig.contentHostingDomain);
    	sb.append(src);
    	sb.append("\" type=\"text/javascript\" language=\"javascript\" charset=\"utf-8\"></script>\n");
    	return sb.toString();
    }

    /**
     * Returns a link tag which can be used to output uncompressed CSS tags
     * within the HTML.
     */
    private static String getLinkTag(String src) {
    	StringBuilder sb = new StringBuilder();
    	sb.append("<link href=\"");
    	if (null != press.PluginConfig.contentHostingDomain && press.PluginConfig.contentHostingDomain.length() > 0)
    		sb.append(press.PluginConfig.contentHostingDomain);
    	sb.append(src);
    	sb.append("\" rel=\"stylesheet\" type=\"text/css\" charset=\"utf-8\">");
    	if (!press.PluginConfig.htmlCompatible)
    		sb.append("</link>");
    	sb.append("\n");
    	return sb.toString();
    }
}
