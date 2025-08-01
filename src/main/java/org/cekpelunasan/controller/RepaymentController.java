package org.cekpelunasan.controller;


import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.service.repayment.RepaymentService;
import org.cekpelunasan.utils.PenaltyUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Controller
public class RepaymentController {

	private static final Logger log = LoggerFactory.getLogger(RepaymentController.class);
	private final RepaymentService repaymentService;
	private final PenaltyUtils penaltyUtils;

	public RepaymentController(RepaymentService repaymentService, PenaltyUtils penaltyUtils) {
		this.repaymentService = repaymentService;
		this.penaltyUtils = penaltyUtils;
	}

	@GetMapping("/pelunasan")
	public String showWebApp(@RequestParam(required = false) String initData, Model model) {
		model.addAttribute("initData", initData);
		model.addAttribute("repayments", repaymentService.findAllLimited());
		log.info("initData: {}", initData);
		return "webapp";
	}

	@GetMapping("/api/search")
	@ResponseBody
	public List<Repayment> searchRepayments(@RequestParam String searchTerm) {
		if (searchTerm == null || searchTerm.trim().isEmpty()) {
			log.info("Searching for all repayments...");
			List<Repayment> allLimited = repaymentService.findAllLimited();
			return getRepayments(allLimited);
		}
		List<Repayment> repayments = repaymentService.searchByIdOrNameLimited(searchTerm.trim());
		return getRepayments(repayments);
	}

	@NotNull
	private List<Repayment> getRepayments(List<Repayment> allLimited) {
		allLimited.forEach(r -> {
			Map<String, Long> penalty = penaltyUtils.penalty(r.getStartDate(), r.getAmount(), r.getProduct(), r);
			r.setPenaltyRepayment(penalty.get("penalty"));
			log.info("Penalty: {}", penalty.get("penalty"));
			log.info("Multiplier : {}", penalty.get("multiplier"));
		});
		return allLimited;
	}

	@GetMapping("/tabungan")
	public String tabungan() {
		return "OK";
	}
	
	@GetMapping("/simulasi")
	public String simulasi() {
		return "OK";
	}
}