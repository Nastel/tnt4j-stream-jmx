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
