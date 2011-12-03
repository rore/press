*{
 *  Outputs a versioned file name whose source is the file
 *  specified as a parameter
 *  When the plugin is disabled, outputs the original file name
 *  for easy debugging.
 *
 *  eg:
 *  <link rel="stylesheet" href="#{press.versioned-file '/public/stylesheets/supersized.core.css'/}" type="text/css" media="screen" />
 *
 *  will output:
 *  <link rel="stylesheet" href="/press/versioned/1311344245525/%257Cpublic%257Cstylesheets%257Csupersized.core.css" type="text/css" media="screen" /> 
 *
 *  See the plugin documentation for more information.
 *  
}*%{
    ( _arg ) &&  ( _src = _arg);
    if(! _src) {
        throw new play.exceptions.TagInternalException("src attribute cannot be empty for press.versioned-file tag");
    }
}%${ press.Plugin.addVersionedFile(_src) }