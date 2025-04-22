package org.cekpelunasan.controller;

import org.cekpelunasan.service.CsvProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class UploadController {

    private final CsvProcessor csvProcessor;

    public UploadController(CsvProcessor csvProcessor) {
        this.csvProcessor = csvProcessor;
    }
    @PostMapping("/upload")
    public ResponseEntity<?> uploadData(@RequestParam("file")MultipartFile file) {
        csvProcessor.processPelunasan(file);
        return ResponseEntity.ok("File uploaded successfully");
    }
    @GetMapping("/upload")
    public RedirectView uploadPage() {
        return new RedirectView("/upload.html"); // atau gunakan Thymeleaf
    }

    @PostMapping("/uploadtagihan")
    public ResponseEntity<?> uploadDataTagihan(@RequestParam("file")MultipartFile file) {
        csvProcessor.processTagihan(file);
        return ResponseEntity.ok("File uploaded successfully");
    }
    @GetMapping("/uploadtagihan")
    public RedirectView uploadPageTagihan() {
        return new RedirectView("/uploadtagihan.html");
    }

}
