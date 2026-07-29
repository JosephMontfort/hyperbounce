Java.perform(function() {
    var View = Java.use('android.view.View');
    var ViewGroup = Java.use('android.view.ViewGroup');

    var targetClasses = [
        "miuix.slidingwidget.widget.SlidingButton",
        "miuix.slidingwidget.widget.SlidingSwitchCompat",
        "miui.widget.SlidingButton"
    ];

    View.dispatchTouchEvent.overload('android.view.MotionEvent').implementation = function(event) {
        var action = event.getActionMasked();
        var className = this.getClass().getName();
        
        // Trigger only on ACTION_DOWN and only on our target toggles
        if (action === 0 && targetClasses.indexOf(className) !== -1) {
            console.log("\n[+] ========================================================");
            console.log("[+] TOUCH CAPTURED ON: " + className);
            
            var currentView = this;
            var depth = 0;
            
            while (currentView !== null) {
                var currentClass = currentView.getClass().getName();
                
                // Extract XML View ID if available
                var viewId = currentView.getId();
                var idName = "NO_ID";
                if (viewId !== -1) {
                    try {
                        idName = currentView.getResources().getResourceEntryName(viewId);
                    } catch(e) {}
                }
                
                // Get standard clipping bounds and Z-index data
                var clipToOutline = currentView.getClipToOutline();
                var elevation = currentView.getElevation();
                var w = currentView.getWidth();
                var h = currentView.getHeight();
                
                // Attempt to cast to ViewGroup for children clipping properties
                var clipChildren = "N/A";
                var clipToPadding = "N/A";
                try {
                    var vg = Java.cast(currentView, ViewGroup);
                    clipChildren = vg.getClipChildren();
                    clipToPadding = vg.getClipToPadding();
                } catch(e) {}
                
                var indent = "  ".repeat(depth);
                console.log(indent + "├─ [" + depth + "] " + currentClass);
                console.log(indent + "│  ID: " + idName + " | Dimens: " + w + "x" + h + " | Elev: " + elevation);
                console.log(indent + "│  ClipChildren: " + clipChildren + " | ClipToPad: " + clipToPadding + " | ClipToOutline: " + clipToOutline);
                
                // Climb up the tree
                var parent = currentView.getParent();
                if (parent !== null) {
                    try {
                        currentView = Java.cast(parent, View);
                        depth++;
                    } catch(e) {
                        console.log(indent + "│  [!] Parent is not a standard View: " + parent.getClass().getName());
                        currentView = null;
                    }
                } else {
                    currentView = null;
                }
            }
            console.log("[+] ========================================================\n");
        }
        return this.dispatchTouchEvent(event);
    };
    console.log("[*] HyperBounce Hook Active. Touch the clipping toggle to dump the hierarchy...");
});
