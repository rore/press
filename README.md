Press plugin for playframework - Multiple servers and CDN support
=================================================================

This is a fork of the press plugin for the Play! framework, which can be found here - 
https://github.com/dirkmc/press

This fork comes to address two issues - using press on a web farm with multiple 
servers, and handling files that are cached on a CDN.

Motivation
----------

The original version of press relies on the local web server cache for returning the 
compressed file. It generates a hash for the file on the HTML, and then looks for 
that hash on the cache for resolving it to the compressed file.
This poses a problem when working in a multiple server environment - one server may
generate the HTML, but another one can get the request for the file and fail since 
it doesn't have the hash in the local cache yet.

The second issue is routing the files through a CDN. We want the CDN to automatically 
re-fetch updated files without the need to manually refresh them on the CDN.

Features
--------

### Multiple Servers
* Press supports a new operation mode - serverFarm. To enable it add in the conf file:
  `press.serverFarm=true` .
  When this mode is set, Press won't use a hash in the requests for the compressed files. 
  Instead it will generate a file name consisting of the combined files and their time.
  Since this file name can be long, if it's too long it will break it to several files.
* The combined files should maintain a certain order, otherwise it can cause Javascript errors.
  To do so, in serverFarm mode you have to specify the order of the combined files. Do it
  by using the new "pos" parameter on the press.script tag:
  `#{press.script '/my.js', pos:1 /}`

### CDN Support
* To route all press file requests through a CDN add to the conf file:
  `press.contentHostingDomain=http://my.cdn.host` .
* Press supports a new "cache buster" mode that will add the timestamp of the file as a request parameter. To enable it
  add in the conf file: `press.cacheBuster=true`
* Cache buster has two modes. In the default mode, press will add the timestamp of the file to all requests 
  generated with the single-script tag (The combined scripts already include the file time).
  That will make the CDN re-fetch the file if the file time is different.
* Some CDN providers (like Amazon CloudFront) doesn't support query strings. To handle this there is a seconds cache buster mode that doesn't use query string. Insead it will append a press suffix to the filename, and press will capture these kind of requests and will return the original file. To enable this mode add in the conf file: `press.cacheBusterInQueryString=false`
* If you need to route other non-js or css files (like images) through the CDN, you can use the new "versioned-file" tag. This will render a CDN versioned file name instead of the original one. Example:
  `<link rel="stylesheet" href="#{press.versioned-file '/public/mystyle.css'/}" type="text/css" media="screen" />`
  will output:
  `<link rel="stylesheet" href="/public/mystyle.css.15432453.press" type="text/css" media="screen" />`
  