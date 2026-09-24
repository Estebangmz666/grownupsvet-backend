package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.controller;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto.StaffBinaryContent;
import org.springframework.http.*;

final class StaffBinaryResponses {
    private StaffBinaryResponses() { }
    static ResponseEntity<byte[]> diploma(StaffBinaryContent content) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .contentType(MediaType.APPLICATION_PDF).contentLength(content.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diploma.pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'; frame-ancestors 'none'")
                .body(content.content());
    }
}
