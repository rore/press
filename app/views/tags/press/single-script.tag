*{
 *  Outputs a <script> tag whose source is the compressed output of the file
 *  specified as a parameter
 *  When the plugin is disabled, outputs a script tag with the original source
 *  for easy debugging.
 *
 *  eg:
 *  #{press.single-script "widget.js"}
 *
 *  will output:
 *  <script src="/public/javascripts/press/widget.min.js" type="text/javascript" language="javascript" charset="utf-8"/>
 *
 *  See the plugin documentation for more information.
 *  
}*%{
    ( _arg ) &&  ( _src = _arg);
    // compress defaults to true
    if(_compress == null) {
      _compress = true;
    }

    if(! _src) {
        throw new play.exceptions.TagInternalException("src attribute cannot be empty for press.single-script tag");
    }
}%${ press.Plugin.addSingleJS(_src, _dir, _compress) }