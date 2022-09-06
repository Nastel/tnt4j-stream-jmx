/*
 * Copyright 2014-2022 JKOOL, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Script to scroll down console output on window load*/
window.onload = function () {
    scrollDownTA ('tnt4j_jmx_output');
    scrollDownTA ('tnt4j_logger_output');
}

/* Script to scroll down textArea */
function scrollDownTA (tAreaId) {
    var textArea = document.getElementById (tAreaId);
    textArea.scrollTop = textArea.scrollHeight;
}

/* Scripts tab  function*/
function openTab (evt, tAreaId) {
    var i,
        tabContent,
        tabLinks,
        showing;

    showing = document.getElementById (tAreaId).style.display === "block";
    tabContent = document.getElementsByClassName ("tabcontent");
    for (i = 0; i < tabContent.length; i++) {
        tabContent[i].style.display = "none";
    }
    tabLinks = document.getElementsByClassName ("tablinks");
    for (i = 0; i < tabLinks.length; i++) {
        tabLinks[i].className = tabLinks[i].className.replace ("active", "");
    }

    if (!showing) {
        document.getElementById (tAreaId).style.display = "block";
        evt.currentTarget.className += " active";
    }
}
