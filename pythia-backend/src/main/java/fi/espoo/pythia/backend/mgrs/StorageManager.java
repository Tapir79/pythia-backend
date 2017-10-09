package fi.espoo.pythia.backend.mgrs;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import fi.espoo.pythia.backend.converters.FileConverter;
import fi.espoo.pythia.backend.encoders.EncoderBase64;
import fi.espoo.pythia.backend.mappers.CommentToCommentValueMapper;
import fi.espoo.pythia.backend.mappers.CommentValueToCommentMapper;
import fi.espoo.pythia.backend.mappers.PlanToPlanValueMapper;
import fi.espoo.pythia.backend.mappers.PlanValueToPlanMapper;
import fi.espoo.pythia.backend.mappers.PrjToPrjVal2Mapper;
import fi.espoo.pythia.backend.mappers.PrjUpToPrjUpValMapper;
import fi.espoo.pythia.backend.mappers.PrjUpValToPrjUpMapper;
import fi.espoo.pythia.backend.mappers.PrjVal2ToPrjMapper;
import fi.espoo.pythia.backend.repos.CommentRepository;
import fi.espoo.pythia.backend.repos.PlanRepository;
import fi.espoo.pythia.backend.repos.ProjectRepository;
import fi.espoo.pythia.backend.repos.ProjectUpdateRepository;
import fi.espoo.pythia.backend.repos.SisterProjectRepository;
import fi.espoo.pythia.backend.repos.SisterProjectUpdateRepository;
import fi.espoo.pythia.backend.repos.entities.Comment;
import fi.espoo.pythia.backend.repos.entities.Plan;
import fi.espoo.pythia.backend.repos.entities.Project;
import fi.espoo.pythia.backend.repos.entities.ProjectUpdate;
import fi.espoo.pythia.backend.repos.entities.SisterProject;
import fi.espoo.pythia.backend.repos.entities.SisterProjectUpdate;
import fi.espoo.pythia.backend.transfer.CommentValue;
import fi.espoo.pythia.backend.transfer.PlanValue;
import fi.espoo.pythia.backend.transfer.ProjectUpdateValue;
import fi.espoo.pythia.backend.transfer.ProjectValue2;

@Component
@Transactional
public class StorageManager {

	@Autowired
	private PlanRepository planRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private SisterProjectRepository sisterProjectRepository;

	@Autowired
	private SisterProjectUpdateRepository sisterProjectUpdateRepository;

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private ProjectUpdateRepository projectUpdateRepository;
	// ---------------------GET------------------------------------

	/**
	 * NEW
	 * 
	 * @return list of projects
	 */
	public ArrayList<ProjectValue2> getProjects2() {

		ArrayList<Project> prjList = (ArrayList<Project>) projectRepository.findAll();
		ArrayList<ProjectValue2> prjValList = new ArrayList();

		// for -loop for prjList

		for (Project p : prjList) {
			// map each project to projectValue
			ProjectValue2 pval = PrjToPrjVal2Mapper.ProjectToProjectValue2(p);
			prjValList.add(pval);
		}
		// return projectValue -ArrayList
		return prjValList;
	}

	/**
	 * Return project object for given id from database. If project is not found
	 * for id, returns null. DONE
	 */
	public ProjectValue2 getProject2(Long projectId) {

		Project project = projectRepository.findByProjectId(projectId);
		ProjectValue2 pval = PrjToPrjVal2Mapper.ProjectToProjectValue2(project);
		return pval;

	}

	/**
	 * 
	 * @param hansuId
	 * @return
	 */
	public ProjectValue2 getProjectByHansuId2(String hansuId) {
		List<Project> prjList = projectRepository.findAll();
		for (Project p : prjList) {
			if (p.getHansuProjectId().equals(hansuId)) {
				ProjectValue2 pval = PrjToPrjVal2Mapper.ProjectToProjectValue2(p);
				return pval;
			}
		}

		return null;
	}

	/**
	 * get all plans by projectId
	 * 
	 * @param projectId
	 * @return
	 */
	public List<PlanValue> getPlans(Long projectId) {

		ProjectUpdate projectUpdate = projectUpdateRepository.findByProjectId(projectId);
		ProjectUpdateValue pval = PrjUpToPrjUpValMapper.ProjectUpdateToProjectUpdateValue(projectUpdate);

		// List<PlanValue> planValues = new ArrayList();

		// for (Plan plan : pval.getPlans()) {
		// // map each plan to planValue
		// PlanValue planValue = PlanToPlanValueMapper.planToPlanValue(plan,
		// project);
		// planValues.add(planValue);
		// }
		return pval.getPlans();

	}

	public PlanValue getPlan(Long planId) {

		Plan plan = planRepository.findByPlanId(planId);
		ProjectUpdate project = plan.getProject();
		PlanValue pVal = PlanToPlanValueMapper.planToPlanValue(plan, project);

		return pVal;
	}

	public CommentValue getComment(long id) {
		Comment comment = commentRepository.findByCommentId(id);
		Plan plan = comment.getPlan();
		CommentValue cVal = CommentToCommentValueMapper.commentToCommentValue(comment, plan);

		return cVal;
	}

	/**
	 * get comments by planId
	 * 
	 * @param planId
	 * @return
	 */
	public List<CommentValue> getComments(Long planId) {

		Plan plan = planRepository.findByPlanId(planId);
		List<Comment> comments = commentRepository.findByPlan(plan);

		List<CommentValue> commentValues = new ArrayList();
		for (Comment c : comments) {
			CommentValue cv = CommentToCommentValueMapper.commentToCommentValue(c, plan);
			commentValues.add(cv);
		}

		// TODO Auto-generated method stub
		return commentValues;

	}

	// ---------------------POST-----------------------------------

	public ProjectUpdate createProject(ProjectUpdateValue projectV) {

		ProjectUpdate projectUpTemp = projectUpdateRepository.findByProjectId(projectV.getProjectId());
		ProjectUpdate projectUp = PrjUpValToPrjUpMapper.projectValue2ToProject(projectV, projectUpTemp, false);
		ProjectUpdate updatedProject = projectUpdateRepository.save(projectUp);
		updateSisterProjects(projectV, projectUp);

		return updatedProject;
	}

	/**
	 * Checks if 1st version and if approved
	 * 
	 * If the 1st then version = 0 and approved = true
	 * 
	 * If not the 1st then increase version number by one
	 * 
	 * @return PlanValue
	 */
	public PlanValue createPlan(PlanValue planV) {

		Long projectId = planV.getProjectId();
		// get project by projectid
		ProjectUpdate projectUpdate = projectUpdateRepository.findByProjectId(projectId);
		// map planV to plan
		Plan mappedPlan = PlanValueToPlanMapper.planValueToPlan(planV, projectUpdate, false);

		// get all plans with planV.projectId and planV.mainNo & planV.subNo

		List<Plan> existingPlans = planRepository.findByProjectInAndMainNoInAndSubNoIn(projectUpdate, planV.getMainNo(),
				planV.getSubNo());

		short version = 0;
		boolean approved = true;
		// first version
		if (existingPlans.isEmpty()) {
			version = 0;
			approved = true;

		} else {
			// sorting from min to max
			Collections.sort(existingPlans);
			// get existingPlans max version with projctId, mainno and subno
			Plan max = existingPlans.get(existingPlans.size() - 1);
			// max version
			short maxVersion = max.getVersion();
			version = (short) (maxVersion + 1);
			approved = false;

		}
		mappedPlan.setVersion(version);
		mappedPlan.setApproved(approved);

		Plan savedPlan = planRepository.save(mappedPlan);

		PlanValue savedPlanValue = PlanToPlanValueMapper.planToPlanValue(savedPlan, projectUpdate);
		// finally
		return savedPlanValue;
	}

	public CommentValue createComment(CommentValue commV, Long id) {

		// Long planId = commV.getPlanId();
		Plan plan = planRepository.findByPlanId(id);

		Comment comm = CommentValueToCommentMapper.commentValueToComment(commV, plan, false);
		Comment savedComm = commentRepository.save(comm);

		CommentValue savedCommValue = CommentToCommentValueMapper.commentToCommentValue(savedComm, plan);
		return savedCommValue;

	}

	// ---------------------PUT------------------------------------

	// /**
	// *
	// * @param projectV
	// * @return
	// */
	// public ProjectValue updateProject(ProjectValue projectV) {
	//
	// Project projectTemp =
	// projectRepository.findByProjectId(projectV.getProjectId());
	// Project project =
	// ProjectValueToProjectMapper.projectValueToProjectUpdate(projectV,
	// projectTemp);
	// Project updatedProject = projectRepository.save(project);
	//
	// ProjectValue updatedProjectValue =
	// ProjectToProjectValueMapper.projectToProjectValue(updatedProject);
	// return updatedProjectValue;
	//
	// }

	public void updateProject(ProjectUpdateValue projectV) {

		ProjectUpdate projectUpTemp = projectUpdateRepository.findByProjectId(projectV.getProjectId());
		ProjectUpdate projectUp = PrjUpValToPrjUpMapper.projectValue2ToProject(projectV, projectUpTemp, true);
		// update basic project table
		projectUpdateRepository.save(projectUp);

		// update sisterProjects table
		updateSisterProjects(projectV, projectUp);

		// ProjectValue2 updatedProjectValue2 = PrjToPrjVal2
		// .ProjectToProjectValue2(projectRepository.findByProjectId(projectV.getProjectId()));
		// return updatedProjectValue2;
	}

	public void updateSisterProjects(ProjectUpdateValue pv, ProjectUpdate projectUp) {

		// get latest by repo id
		ProjectUpdate p = projectUpdateRepository.findByProjectId(pv.getProjectId());
		// create empty sisterprojects list
		List<SisterProject> sProjects = new ArrayList<SisterProject>();

		// first delete all rows with this project
		sisterProjectUpdateRepository.deleteByProject(projectUp);
		Long j = 1L;
		// add sisterprojects to the db
		for (Long id : pv.getSisterProjects()) {
			System.out.println("updatesisterProjectId:" + id);
			SisterProjectUpdate sProjectUp = (new SisterProjectUpdate(j, projectUp, id));

			sisterProjectUpdateRepository.save(sProjectUp);
			j++;
		}
	}

	/**
	 * 
	 * @param planV
	 * @return
	 */

	public PlanValue updatePlan(PlanValue planV) {

		Long id = planV.getProjectId();
		ProjectUpdate projectUp = projectUpdateRepository.findByProjectId(id);
		Plan plan = PlanValueToPlanMapper.planValueToPlan(planV, projectUp, true);

		Plan updatedPlan = planRepository.save(plan);

		PlanValue updatedPlanValue = PlanToPlanValueMapper.planToPlanValue(updatedPlan, projectUp);
		return updatedPlanValue;

	}

	public CommentValue updateComment(CommentValue commV) {

		Long id = commV.getPlanId();
		Plan plan = planRepository.findByPlanId(id);
		Comment comm = CommentValueToCommentMapper.commentValueToComment(commV, plan, true);

		Comment updatedComm = commentRepository.save(comm);

		CommentValue updatedCommentValue = CommentToCommentValueMapper.commentToCommentValue(updatedComm, plan);
		return updatedCommentValue;

	}

}
