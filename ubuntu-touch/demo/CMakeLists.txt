cmake_minimum_required(VERSION 3.16)
project(liftoff-demo NONE)

# www/index.html is copied from docs/spherelauncher13.html by sync-www.sh before the build
# (Clickable mounts only this directory into its container). It is gitignored.
if(NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/www/index.html)
  message(FATAL_ERROR "www/index.html missing: run ./sync-www.sh first")
endif()


# Clickable fills @CLICK_ARCH@ / @CLICK_FRAMEWORK@ in manifest.json itself.
install(FILES manifest.json.in RENAME manifest.json DESTINATION ${CMAKE_INSTALL_PREFIX})
install(FILES liftoff-demo.apparmor liftoff-demo.desktop DESTINATION ${CMAKE_INSTALL_PREFIX})
install(DIRECTORY www DESTINATION ${CMAKE_INSTALL_PREFIX})
install(DIRECTORY assets DESTINATION ${CMAKE_INSTALL_PREFIX})
