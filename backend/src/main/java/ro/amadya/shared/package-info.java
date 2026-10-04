/**
 * Shared kernel: ids, money, localized text, errors, security and web configuration.
 * Open module: it must not depend on any business module.
 */
@ApplicationModule(displayName = "Shared kernel", type = ApplicationModule.Type.OPEN)
package ro.amadya.shared;

import org.springframework.modulith.ApplicationModule;
