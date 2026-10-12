// Puente Objective-C hacia AppKit (llamado desde Kotlin con JNA).
// AppKit solo se puede tocar desde el hilo principal: todo pasa por on_main().
#import <AppKit/AppKit.h>
#import <AVFoundation/AVFoundation.h>
#import <objc/runtime.h>

static void on_main(void (^block)(void)) {
    if ([NSThread isMainThread]) block();
    else dispatch_sync(dispatch_get_main_queue(), block);
}

// Última app (distinta de esta) que estuvo activa; la usa el "guardián de foco".
static NSRunningApplication *g_last_app = nil;
static id g_observer = nil;

int dictado_macos_ping(void) { return 1; }

// Convierte la app en "accesoria": sin icono en el Dock ni en ⌘Tab.
void dictado_macos_set_accessory(void) {
    on_main(^{ [NSApp setActivationPolicy:NSApplicationActivationPolicyAccessory]; });
}

// Configura la ventana con el título dado como bolita flotante.
// Devuelve 0 = ok (es NSPanel), 1 = ventana no encontrada, 2 = encontrada pero no es NSPanel.
// 'info' recibe una descripción (clase, estilo, nivel) para diagnóstico.
int dictado_macos_configure_floating(const char *title, char *info, int info_len) {
    __block int rc = 1;
    NSString *wanted = [NSString stringWithUTF8String:title];
    on_main(^{
        for (NSWindow *w in [NSApp windows]) {
            if (![w.title isEqualToString:wanted]) continue;
            [w setCollectionBehavior:NSWindowCollectionBehaviorCanJoinAllSpaces |
                                     NSWindowCollectionBehaviorFullScreenAuxiliary |
                                     NSWindowCollectionBehaviorStationary |
                                     NSWindowCollectionBehaviorIgnoresCycle];
            [w setHidesOnDeactivate:NO];
            if ([w isKindOfClass:[NSPanel class]]) {
                NSPanel *p = (NSPanel *)w;
                p.styleMask |= NSWindowStyleMaskNonactivatingPanel;
                [p setBecomesKeyOnlyIfNeeded:YES];
                [p setFloatingPanel:YES];
                rc = 0;
            } else {
                rc = 2;
            }
            [w setLevel:NSStatusWindowLevel]; // después de setFloatingPanel, que lo reinicia a 'floating'
            if (info && info_len > 0) {
                snprintf(info, (size_t)info_len, "class=%s styleMask=0x%lx level=%ld canBecomeKey=%d",
                         class_getName([w class]), (unsigned long)w.styleMask, (long)w.level,
                         (int)w.canBecomeKeyWindow);
            }
            break;
        }
    });
    return rc;
}

// Nombre de la app que está al frente (para comprobar si la bolita le roba el foco).
int dictado_macos_frontmost_app(char *buf, int len) {
    __block int rc = 1;
    @autoreleasepool {
        NSRunningApplication *app = [[NSWorkspace sharedWorkspace] frontmostApplication];
        if (app && buf && len > 0) {
            snprintf(buf, (size_t)len, "%s", [(app.localizedName ?: @"?") UTF8String]);
            rc = 0;
        }
    }
    return rc;
}

// Plan B de foco: recuerda la última app activa que no sea esta.
void dictado_macos_start_focus_guard(void) {
    on_main(^{
        if (g_observer) return;
        pid_t me = [[NSProcessInfo processInfo] processIdentifier];
        NSRunningApplication *front = [[NSWorkspace sharedWorkspace] frontmostApplication];
        if (front && front.processIdentifier != me) g_last_app = front;
        g_observer = [[[NSWorkspace sharedWorkspace] notificationCenter]
            addObserverForName:NSWorkspaceDidActivateApplicationNotification
                        object:nil
                         queue:[NSOperationQueue mainQueue]
                    usingBlock:^(NSNotification *n) {
            NSRunningApplication *app = n.userInfo[NSWorkspaceApplicationKey];
            if (app && app.processIdentifier != me) g_last_app = app;
        }];
    });
}

// Devuelve el foco a la última app activa (si esta app lo tomó).
void dictado_macos_restore_focus(void) {
    on_main(^{
        pid_t me = [[NSProcessInfo processInfo] processIdentifier];
        NSRunningApplication *front = [[NSWorkspace sharedWorkspace] frontmostApplication];
        if (g_last_app && front && front.processIdentifier == me) {
            [g_last_app activateWithOptions:0];
        }
    });
}

// Diagnóstico de foco: ¿esta app está activa? ¿alguna de sus ventanas es la ventana "key"?
int dictado_macos_focus_state(char *buf, int len) {
    __block int rc = 1;
    on_main(^{
        NSWindow *key = [NSApp keyWindow];
        NSWindow *main = [NSApp mainWindow];
        NSRunningApplication *front = [[NSWorkspace sharedWorkspace] frontmostApplication];
        snprintf(buf, (size_t)len, "appActiva=%d ventanaKey=%s ventanaMain=%s alFrente=%s",
                 (int)[NSApp isActive],
                 key ? "SI" : "no", main ? "SI" : "no",
                 [(front.localizedName ?: @"?") UTF8String]);
        rc = 0;
    });
    return rc;
}

// Permiso de micrófono. Estado: 0 = sin decidir, 1 = restringido, 2 = denegado, 3 = autorizado.
int dictado_macos_mic_status(void) {
    return (int)[AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeAudio];
}

// Pide el permiso (muestra el diálogo de macOS la primera vez) y espera la respuesta.
// No debe llamarse desde el hilo principal. Devuelve 1 si se concedió.
int dictado_macos_request_mic(void) {
    __block int granted = 0;
    dispatch_semaphore_t sem = dispatch_semaphore_create(0);
    [AVCaptureDevice requestAccessForMediaType:AVMediaTypeAudio completionHandler:^(BOOL g) {
        granted = g ? 1 : 0;
        dispatch_semaphore_signal(sem);
    }];
    dispatch_semaphore_wait(sem, DISPATCH_TIME_FOREVER);
    return granted;
}
