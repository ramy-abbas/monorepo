package org.sakaiproject.scorm.model.api;

public class SeqActivitySnapshot {

	private Long id;

	private String activityId;

	private String resourceId;

	private String scoId;

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SeqActivitySnapshot other = (SeqActivitySnapshot) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	public String getActivityId() {
		return activityId;
	}

	public String getResourceId() {
		return resourceId;
	}

	public String getScoId() {
		return scoId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	public void setActivityId(String activityId) {
		this.activityId = activityId;
	}

	public void setResourceId(String resourceId) {
		this.resourceId = resourceId;
	}

	public void setScoId(String scoId) {
		this.scoId = scoId;
	}

}
