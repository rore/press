package controllers.press;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Locale;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import play.Play;
import play.exceptions.UnexpectedException;
import play.libs.MimeTypes;
import play.mvc.Controller;
import play.mvc.Http;
import play.utils.Utils;
import play.vfs.VirtualFile;
import press.CSSCompressor;
import press.CachingStrategy;
import press.JSCompressor;
import press.Plugin;
import press.PluginConfig;
import press.io.CompressedFile;
import press.io.FileIO;

public class Press extends Controller {
	public static final DateTimeFormatter httpDateTimeFormatter = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withLocale(Locale.US);
	
	static boolean eTag_ = Play.configuration.getProperty("http.useETag", "true").equalsIgnoreCase("true");

    public static void handleVersionedFile(String path) {
    	if (null == path) {
    		notFound();
    		return;
    	}
    	// we need to remove the filetime suffix 
    	int pos = path.lastIndexOf('.');
    	if (pos > -1){
    		path = path.substring(0, pos);
    	}
    	VirtualFile file = FileIO.getVirtualFile(path);
        renderFile(file, path);
    }

    public static void getCompressedJS(String key) {
        key = FileIO.unescape(key);
        CompressedFile compressedFile = JSCompressor.getCompressedFile(key);
        renderCompressedFile(compressedFile, "JavaScript");
    }

    public static void getCompressedCSS(String key) {
        key = FileIO.unescape(key);
        CompressedFile compressedFile = CSSCompressor.getCompressedFile(key);
        renderCompressedFile(compressedFile, "CSS");
    }

    public static void getSingleCompressedJS(String key) {
        key = FileIO.unescape(key);
        CompressedFile compressedFile = JSCompressor.getSingleCompressedFile(key);
        renderCompressedFile(compressedFile, "JavaScript");
    }

    public static void getSingleCompressedCSS(String key) {
        key = FileIO.unescape(key);
        CompressedFile compressedFile = CSSCompressor.getSingleCompressedFile(key);
        renderCompressedFile(compressedFile, "CSS");
    }

    private static void renderFile(VirtualFile file, String fileName) {
        if (file == null || !file.exists()) {
            //renderBadResponse(fileName);
        	notFound();
        	return;
        }

        String mimeType = MimeTypes.getContentType(fileName); 
        // check for last modified
        long l = file.lastModified();
    	final String etag = "\"" + l + "-" + file.hashCode() + "\"";

    	if (l > 0){
    		// if the file is not modified, return 304
            if (!request.isModified(etag, l)){
                if (request.method.equalsIgnoreCase("GET")) {
                	response.status = Http.StatusCode.NOT_MODIFIED;
                    if (eTag_) {
                    	response.setHeader("Etag", etag);
                        //response.setHeader("Content-Type", mimeType);
                    }
                }
                return;
            }
        }
        
        InputStream inputStream = file.inputstream();

        // This seems to be buggy, so instead of passing the file length we
        // reset the input stream and allow play to manually copy the bytes from
        // the input stream to the response
        // renderBinary(inputStream, compressedFile.name(),
        // compressedFile.length());

        try {
            if(inputStream.markSupported()) {
                inputStream.reset();
            }
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
        
        // special handling for sass - let the plugin a change to handle this
        boolean raw = Play.pluginCollection.serveStatic(file, request, response);

        // If the caching strategy is always, the timestamp is not part of the key. If 
        // we let the browser cache, then the browser will keep holding old copies, even after
        // changing the files at the server and restarting the server, since the key will
        // stay the same.
        // If the caching strategy is never, we also don't want to cache at the browser, for 
        // obvious reasons.
        // If the caching strategy is Change, then the modified timestamp is a part of the key, 
        // so if the file changes, the key in the html file will be modified, and the browser will
        // request a new version. Each version can therefore be cached indefinitely.
        if(PluginConfig.cache.equals(CachingStrategy.Change)) {
        	response.setHeader("Cache-Control", "max-age=" + 31536000); // A year
        	response.setHeader("Expires", httpDateTimeFormatter.print(new DateTime().plusYears(1)));
        	response.setHeader("Last-Modified", Utils.getHttpDateFormatter().format(new Date(l + 1000)));
        	if (!raw)
        		response.setHeader("Content-Type", mimeType);
            if (eTag_) {
            	response.setHeader("Etag", etag);
            }
        }
        if (raw) {
        	try {
        		inputStream.close();
            } catch (IOException e) {
                throw new UnexpectedException(e);
            }
        }
        else{
            renderBinary(inputStream, file.getName(), true);
        }
    }
    
    private static void renderCompressedFile(CompressedFile compressedFile, String type) {
        if (compressedFile == null) {
            //renderBadResponse(type);
            notFound();
            return;
        }

        String contentType = "application/javascript; charset=utf-8";
        if(type.equals("CSS")) {
            contentType = "text/css; charset=utf-8";
        }

        // check for last modified
        long l = compressedFile.lastModified();
    	final String etag = "\"" + l + "-" + compressedFile.originalHashCode() + "\"";

    	if (l > 0){
    		// if the file is not modified, return 304
            if (!request.isModified(etag, l)){
                if (request.method.equalsIgnoreCase("GET")) {
                	response.status = Http.StatusCode.NOT_MODIFIED;
                    if (eTag_) {
                    	response.setHeader("Etag", etag);
                        response.setHeader("Content-Type", contentType);

                    }
                }
                return;
            }
        }
        
        InputStream inputStream = compressedFile.inputStream();

        // This seems to be buggy, so instead of passing the file length we
        // reset the input stream and allow play to manually copy the bytes from
        // the input stream to the response
        // renderBinary(inputStream, compressedFile.name(),
        // compressedFile.length());

        try {
            if(inputStream.markSupported()) {
                inputStream.reset();
            }
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
        
        // If the caching strategy is always, the timestamp is not part of the key. If 
        // we let the browser cache, then the browser will keep holding old copies, even after
        // changing the files at the server and restarting the server, since the key will
        // stay the same.
        // If the caching strategy is never, we also don't want to cache at the browser, for 
        // obvious reasons.
        // If the caching strategy is Change, then the modified timestamp is a part of the key, 
        // so if the file changes, the key in the html file will be modified, and the browser will
        // request a new version. Each version can therefore be cached indefinitely.
        if(PluginConfig.cache.equals(CachingStrategy.Change)) {
        	response.setHeader("Cache-Control", "max-age=" + 31536000); // A year
        	response.setHeader("Expires", httpDateTimeFormatter.print(new DateTime().plusYears(1)));
        	response.setHeader("Last-Modified", Utils.getHttpDateFormatter().format(new Date(l + 1000)));
            response.setHeader("Content-Type", contentType);
            if (eTag_) {
            	response.setHeader("Etag", etag);
            }
        }
        renderBinary(inputStream, compressedFile.name(), true);
    }

    public static void clearJSCache() {
        if (!PluginConfig.cacheClearEnabled) {
            forbidden();
        }

        int count = JSCompressor.clearCache();
        renderText("Cleared " + count + " JS files from cache");
    }

    public static void clearCSSCache() {
        if (!PluginConfig.cacheClearEnabled) {
            forbidden();
        }

        int count = CSSCompressor.clearCache();
        renderText("Cleared " + count + " CSS files from cache");
    }

    private static void renderBadResponse(String fileType) {
        String response = "/*\n";
        response += "The compressed " + fileType + " file could not be generated.\n";
        response += "This can occur in two situations:\n";
        response += "1. The time between when the page was rendered by the ";
        response += "server and when the browser requested the compressed ";
        response += "file was greater than the timeout. (The timeout is ";
        response += "currently configured to be ";
        response += PluginConfig.compressionKeyStorageTime + ")\n";
        response += "2. There was an exception thrown while rendering the ";
        response += "page.\n";
        response += "*/";
        renderBinaryResponse(response);
    }

    private static void renderBinaryResponse(String response) {
        try {
            renderBinary(new ByteArrayInputStream(response.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new UnexpectedException(e);
        }
    }
}